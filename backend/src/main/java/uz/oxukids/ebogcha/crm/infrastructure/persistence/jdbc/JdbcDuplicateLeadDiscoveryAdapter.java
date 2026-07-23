package uz.oxukids.ebogcha.crm.infrastructure.persistence.jdbc;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import uz.oxukids.ebogcha.crm.application.port.out.DuplicateLeadDiscoveryPort;
import uz.oxukids.ebogcha.crm.domain.model.PhoneNumber;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Repository
public class JdbcDuplicateLeadDiscoveryAdapter implements DuplicateLeadDiscoveryPort {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcDuplicateLeadDiscoveryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
    }

    @Override
    public Set<UUID> findCandidateIds(
            UUID organizationId,
            PhoneNumber normalizedPhone,
            UUID excludedLeadId
    ) {
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(normalizedPhone, "normalizedPhone must not be null");

        List<UUID> candidates = jdbc.queryForList(
                """
                SELECT DISTINCT l.id
                  FROM lead_phones lp
                  JOIN leads l ON l.id = lp.lead_id
                  JOIN branches b ON b.id = l.branch_id
                  JOIN organizations o ON o.id = b.organization_id
                 WHERE o.id = :organizationId
                   AND lp.normalized_phone = :normalizedPhone
                   AND (:excludedLeadId IS NULL OR l.id <> :excludedLeadId)
                 ORDER BY l.id
                """,
                new MapSqlParameterSource()
                        .addValue("organizationId", organizationId)
                        .addValue("normalizedPhone", normalizedPhone.canonicalValue())
                        .addValue("excludedLeadId", excludedLeadId),
                UUID.class
        );
        return Collections.unmodifiableSet(new LinkedHashSet<>(candidates));
    }
}
