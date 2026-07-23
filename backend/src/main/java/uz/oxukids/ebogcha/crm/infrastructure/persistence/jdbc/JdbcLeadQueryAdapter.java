package uz.oxukids.ebogcha.crm.infrastructure.persistence.jdbc;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import uz.oxukids.ebogcha.crm.application.port.in.LeadListItem;
import uz.oxukids.ebogcha.crm.application.port.in.OwnerState;
import uz.oxukids.ebogcha.crm.application.port.in.SearchLeadsQuery;
import uz.oxukids.ebogcha.crm.application.port.out.LeadQueryPort;
import uz.oxukids.ebogcha.crm.application.port.out.LeadQueryResult;
import uz.oxukids.ebogcha.crm.domain.model.LeadSource;
import uz.oxukids.ebogcha.crm.domain.model.LeadStatus;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Repository
public class JdbcLeadQueryAdapter implements LeadQueryPort {

    private static final String AUTHORIZED_LEADS_FROM = """
            FROM leads l
            JOIN branches b
              ON b.id = l.branch_id
            JOIN lead_sources source_ref
              ON source_ref.id = l.source_id
             AND source_ref.organization_id = b.organization_id
            JOIN lead_statuses status_ref
              ON status_ref.id = l.status_id
             AND status_ref.organization_id = b.organization_id
            JOIN lead_phones primary_phone
              ON primary_phone.lead_id = l.id
             AND primary_phone.is_primary
            LEFT JOIN users owner_user
              ON owner_user.id = l.owner_user_id
             AND owner_user.organization_id = b.organization_id
            WHERE EXISTS (
                SELECT 1
                  FROM users actor_user
                  JOIN user_branch_access actor_access
                    ON actor_access.user_id = actor_user.id
                   AND actor_access.branch_id = l.branch_id
                 WHERE actor_user.id = :actorUserId
                   AND actor_user.organization_id = b.organization_id
            )
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcLeadQueryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasBranchAccess(UUID actorUserId, UUID branchId) {
        Objects.requireNonNull(actorUserId, "actorUserId must not be null");
        Objects.requireNonNull(branchId, "branchId must not be null");
        try {
            Boolean allowed = jdbc.queryForObject(
                    """
                    SELECT EXISTS (
                        SELECT 1
                          FROM users actor_user
                          JOIN user_branch_access actor_access
                            ON actor_access.user_id = actor_user.id
                          JOIN branches accessible_branch
                            ON accessible_branch.id = actor_access.branch_id
                         WHERE actor_user.id = :actorUserId
                           AND actor_access.branch_id = :branchId
                           AND accessible_branch.organization_id = actor_user.organization_id
                    )
                    """,
                    new MapSqlParameterSource()
                            .addValue("actorUserId", actorUserId)
                            .addValue("branchId", branchId),
                    Boolean.class
            );
            return Boolean.TRUE.equals(allowed);
        } catch (DataAccessException exception) {
            throw new CrmPersistenceException("CRM branch access could not be checked", exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public LeadQueryResult search(SearchLeadsQuery query, Instant asOf) {
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(asOf, "asOf must not be null");

        QueryParts queryParts = buildQueryParts(query, asOf);
        String countSql = "SELECT COUNT(*) " + AUTHORIZED_LEADS_FROM + queryParts.whereSuffix();
        String dataSql = """
                SELECT l.id,
                       b.organization_id,
                       l.branch_id,
                       b.name AS branch_name,
                       source_ref.code AS source_code,
                       status_ref.code AS status_code,
                       l.parent_or_guardian_name,
                       primary_phone.display_phone,
                       l.owner_user_id,
                       owner_user.display_name AS owner_display_name,
                       l.created_at,
                       l.updated_at,
                       l.first_contact_due_at,
                       (
                           status_ref.code = 'NEW'
                           AND l.first_contact_due_at < :asOf
                       ) AS overdue
                """ + AUTHORIZED_LEADS_FROM + queryParts.whereSuffix() + """
                 ORDER BY l.created_at DESC, l.id DESC
                 LIMIT :size OFFSET :offset
                """;
        MapSqlParameterSource parameters = queryParts.parameters()
                .addValue("size", query.size())
                .addValue("offset", query.offset());
        try {
            Long totalElements = jdbc.queryForObject(countSql, parameters, Long.class);
            List<LeadListItem> items = jdbc.query(
                    dataSql,
                    parameters,
                    (resultSet, rowNumber) -> mapItem(resultSet)
            );
            return new LeadQueryResult(
                    items,
                    totalElements == null ? 0 : totalElements
            );
        } catch (DataAccessException exception) {
            throw new CrmPersistenceException("CRM leads could not be queried", exception);
        }
    }

    private static QueryParts buildQueryParts(SearchLeadsQuery query, Instant asOf) {
        List<String> predicates = new ArrayList<>();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("actorUserId", query.actorUserId())
                .addValue("asOf", toUtcOffsetDateTime(asOf));

        if (query.queryText() != null) {
            String digits = digitsOnly(query.queryText());
            boolean phoneSearchEnabled = isPhoneSearch(query.queryText(), digits);
            predicates.add("""
                    AND (
                        l.parent_or_guardian_name ILIKE :namePattern ESCAPE '\\'
                        OR (
                            :phoneSearchEnabled
                            AND regexp_replace(
                                primary_phone.normalized_phone, '[^0-9]', '', 'g'
                            ) LIKE :phonePattern
                        )
                    )
                    """);
            parameters
                    .addValue("namePattern", "%" + escapeLike(query.queryText()) + "%")
                    .addValue("phoneSearchEnabled", phoneSearchEnabled)
                    .addValue("phonePattern", "%" + digits + "%");
        }
        if (query.branchId() != null) {
            predicates.add("AND l.branch_id = :branchId");
            parameters.addValue("branchId", query.branchId());
        }
        if (query.status() != null) {
            predicates.add("AND status_ref.code = :statusCode");
            parameters.addValue("statusCode", query.status().name());
        }
        if (query.source() != null) {
            predicates.add("AND source_ref.code = :sourceCode");
            parameters.addValue("sourceCode", query.source().name());
        }
        if (query.ownerOperatorId() != null) {
            predicates.add("AND l.owner_user_id = :ownerOperatorId");
            parameters.addValue("ownerOperatorId", query.ownerOperatorId());
        }
        if (query.ownerState() == OwnerState.ASSIGNED) {
            predicates.add("AND l.owner_user_id IS NOT NULL");
        } else if (query.ownerState() == OwnerState.UNASSIGNED) {
            predicates.add("AND l.owner_user_id IS NULL");
        }
        if (query.overdueOnly()) {
            predicates.add("AND status_ref.code = 'NEW' AND l.first_contact_due_at < :asOf");
        }
        if (query.createdFrom() != null) {
            predicates.add("AND l.created_at >= :createdFrom");
            parameters.addValue("createdFrom", toUtcOffsetDateTime(query.createdFrom()));
        }
        if (query.createdTo() != null) {
            predicates.add("AND l.created_at < :createdTo");
            parameters.addValue("createdTo", toUtcOffsetDateTime(query.createdTo()));
        }
        return new QueryParts("\n" + String.join("\n", predicates) + "\n", parameters);
    }

    private static OffsetDateTime toUtcOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static LeadListItem mapItem(ResultSet resultSet) throws SQLException {
        return new LeadListItem(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("organization_id", UUID.class),
                resultSet.getObject("branch_id", UUID.class),
                resultSet.getString("branch_name"),
                LeadSource.valueOf(resultSet.getString("source_code")),
                LeadStatus.valueOf(resultSet.getString("status_code")),
                resultSet.getString("parent_or_guardian_name"),
                resultSet.getString("display_phone"),
                resultSet.getObject("owner_user_id", UUID.class),
                resultSet.getString("owner_display_name"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getTimestamp("first_contact_due_at").toInstant(),
                resultSet.getBoolean("overdue")
        );
    }

    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private static String digitsOnly(String value) {
        return value.chars()
                .filter(Character::isDigit)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    private static boolean isPhoneSearch(String value, String digits) {
        return !digits.isEmpty() && value.codePoints().allMatch(character ->
                Character.isDigit(character)
                        || Character.isWhitespace(character)
                        || character == '+'
                        || character == '-'
                        || character == '('
                        || character == ')'
        );
    }

    private record QueryParts(String whereSuffix, MapSqlParameterSource parameters) {
    }
}
