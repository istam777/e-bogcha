package uz.oxukids.ebogcha.crm.infrastructure.persistence.jdbc;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import uz.oxukids.ebogcha.crm.application.port.out.LeadRepository;
import uz.oxukids.ebogcha.crm.domain.exception.LeadAlreadyOwnedException;
import uz.oxukids.ebogcha.crm.domain.exception.LeadNotFoundException;
import uz.oxukids.ebogcha.crm.domain.model.Lead;
import uz.oxukids.ebogcha.crm.domain.model.LeadSource;
import uz.oxukids.ebogcha.crm.domain.model.LeadStatus;
import uz.oxukids.ebogcha.crm.domain.model.PhoneNumber;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcLeadRepository implements LeadRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcLeadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
    }

    @Override
    public Optional<Lead> findById(UUID leadId) {
        Objects.requireNonNull(leadId, "leadId must not be null");
        List<PersistedLead> rows = jdbc.query(
                """
                SELECT l.id,
                       b.organization_id,
                       l.branch_id,
                       ls.code AS source_code,
                       ls.organization_id AS source_organization_id,
                       st.code AS status_code,
                       st.organization_id AS status_organization_id,
                       l.parent_or_guardian_name,
                       lp.normalized_phone,
                       lp.display_phone,
                       l.owner_user_id AS projected_owner_id,
                       la.assigned_user_id AS active_owner_id,
                       l.lost_reason_id,
                       lr.organization_id AS lost_reason_organization_id,
                       l.created_at,
                       l.first_contact_due_at
                  FROM leads l
                  JOIN branches b ON b.id = l.branch_id
                  JOIN lead_sources ls ON ls.id = l.source_id
                  JOIN lead_statuses st ON st.id = l.status_id
                  LEFT JOIN lost_reasons lr ON lr.id = l.lost_reason_id
                  LEFT JOIN lead_phones lp
                    ON lp.lead_id = l.id
                   AND lp.is_primary
                  LEFT JOIN lead_assignments la
                    ON la.lead_id = l.id
                   AND la.ended_at IS NULL
                 WHERE l.id = :leadId
                """,
                new MapSqlParameterSource("leadId", leadId),
                (resultSet, rowNumber) -> mapPersistedLead(resultSet)
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        if (rows.size() != 1) {
            throw new InconsistentCrmDataException("Lead has multiple active persistence rows");
        }
        return Optional.of(reconstitute(rows.getFirst()));
    }

    @Override
    @Transactional
    public void saveNew(Lead lead) {
        Objects.requireNonNull(lead, "lead must not be null");
        requireBranchOrganization(lead.branchId(), lead.organizationId());
        UUID sourceId = resolveSourceId(lead.organizationId(), lead.source());
        UUID statusId = resolveInitialStatusId(lead.organizationId());

        MapSqlParameterSource leadParameters = new MapSqlParameterSource()
                .addValue("id", lead.id())
                .addValue("branchId", lead.branchId())
                .addValue("sourceId", sourceId)
                .addValue("statusId", statusId)
                .addValue("guardianName", lead.parentOrGuardianName())
                .addValue("firstContactDueAt", databaseTime(lead.firstContactDueAt()))
                .addValue("createdAt", databaseTime(lead.createdAt()));
        try {
            jdbc.update(
                    """
                    INSERT INTO leads (
                        id, branch_id, source_id, status_id, parent_or_guardian_name,
                        first_contact_due_at, created_at, updated_at
                    ) VALUES (
                        :id, :branchId, :sourceId, :statusId, :guardianName,
                        :firstContactDueAt, :createdAt, :createdAt
                    )
                    """,
                    leadParameters
            );
            jdbc.update(
                    """
                    INSERT INTO lead_phones (
                        id, lead_id, normalized_phone, display_phone, is_primary, created_at
                    ) VALUES (
                        :phoneId, :leadId, :normalizedPhone, :displayPhone, TRUE, :createdAt
                    )
                    """,
                    new MapSqlParameterSource()
                            .addValue("phoneId", UUID.randomUUID())
                            .addValue("leadId", lead.id())
                            .addValue("normalizedPhone", lead.primaryPhone().canonicalValue())
                            .addValue("displayPhone", lead.primaryDisplayPhone())
                            .addValue("createdAt", databaseTime(lead.createdAt()))
            );
        } catch (DataIntegrityViolationException exception) {
            throw new CrmPersistenceException("New lead could not be persisted", exception);
        }
    }

    @Override
    @Transactional
    public void saveStatusChange(
            Lead lead,
            LeadStatus previousStatus,
            Instant changedAt,
            UUID changedByUserId
    ) {
        Objects.requireNonNull(lead, "lead must not be null");
        Objects.requireNonNull(previousStatus, "previousStatus must not be null");
        Objects.requireNonNull(changedAt, "changedAt must not be null");
        Objects.requireNonNull(changedByUserId, "changedByUserId must not be null");

        StatusState current = lockStatusState(lead.id());
        if (!current.organizationId().equals(lead.organizationId())) {
            throw new InconsistentCrmDataException("Lead organization does not match persisted data");
        }
        requireUserBranchAccess(
                changedByUserId, current.organizationId(), current.branchId()
        );
        if (!current.statusCode().equals(previousStatus.name())) {
            throw new InconsistentCrmDataException("Persisted lead status changed concurrently");
        }
        if (current.statusCode().equals(lead.status().name())) {
            return;
        }
        UUID targetStatusId = resolveStatusId(current.organizationId(), lead.status());
        UUID lostReasonId = lead.lostReasonId().orElse(null);
        if (lostReasonId != null) {
            requireLostReasonOrganization(lostReasonId, current.organizationId());
        }

        try {
            int updated = jdbc.update(
                    """
                    UPDATE leads
                       SET status_id = :targetStatusId,
                           lost_reason_id = :lostReasonId,
                           archived_at = CASE
                               WHEN :targetStatusCode = 'ARCHIVED' THEN :changedAt
                               ELSE NULL
                           END,
                           updated_at = :changedAt,
                           updated_by = :changedByUserId
                     WHERE id = :leadId
                       AND status_id = :previousStatusId
                    """,
                    new MapSqlParameterSource()
                            .addValue("targetStatusId", targetStatusId)
                            .addValue("lostReasonId", lostReasonId)
                            .addValue("targetStatusCode", lead.status().name())
                            .addValue("changedAt", databaseTime(changedAt))
                            .addValue("changedByUserId", changedByUserId)
                            .addValue("leadId", lead.id())
                            .addValue("previousStatusId", current.statusId())
            );
            if (updated != 1) {
                throw new InconsistentCrmDataException("Lead status update lost its concurrency guard");
            }
            jdbc.update(
                    """
                    INSERT INTO lead_status_history (
                        id, lead_id, from_status_id, to_status_id, changed_by, changed_at
                    ) VALUES (
                        :id, :leadId, :fromStatusId, :toStatusId, :changedBy, :changedAt
                    )
                    """,
                    new MapSqlParameterSource()
                            .addValue("id", UUID.randomUUID())
                            .addValue("leadId", lead.id())
                            .addValue("fromStatusId", current.statusId())
                            .addValue("toStatusId", targetStatusId)
                            .addValue("changedBy", changedByUserId)
                            .addValue("changedAt", databaseTime(changedAt))
            );
        } catch (DataIntegrityViolationException exception) {
            throw new CrmPersistenceException("Lead status change could not be persisted", exception);
        }
    }

    @Override
    @Transactional
    public Lead claimOwnership(UUID leadId, UUID operatorId) {
        Objects.requireNonNull(leadId, "leadId must not be null");
        Objects.requireNonNull(operatorId, "operatorId must not be null");

        OwnershipState state = lockOwnershipState(leadId);
        requireUserBranchAccess(
                operatorId, state.organizationId(), state.branchId()
        );
        if (state.activeOwnerId() != null) {
            assertOwnerProjection(state);
            if (state.activeOwnerId().equals(operatorId)) {
                return requireLead(leadId);
            }
            throw new LeadAlreadyOwnedException(leadId);
        }
        if (state.projectedOwnerId() != null) {
            throw new InconsistentCrmDataException("Lead owner projection has no active assignment");
        }

        Instant assignedAt = Instant.now();
        try {
            int inserted = jdbc.update(
                    """
                    INSERT INTO lead_assignments (
                        id, lead_id, assigned_user_id, assigned_by, assigned_at
                    ) VALUES (
                        :id, :leadId, :operatorId, :operatorId, :assignedAt
                    )
                    ON CONFLICT (lead_id) WHERE ended_at IS NULL DO NOTHING
                    """,
                    new MapSqlParameterSource()
                            .addValue("id", UUID.randomUUID())
                            .addValue("leadId", leadId)
                            .addValue("operatorId", operatorId)
                            .addValue("assignedAt", databaseTime(assignedAt))
            );
            if (inserted == 0) {
                UUID competingOwner = activeOwnerId(leadId);
                if (!operatorId.equals(competingOwner)) {
                    throw new LeadAlreadyOwnedException(leadId);
                }
            }
            jdbc.update(
                    """
                    UPDATE leads
                       SET owner_user_id = :operatorId,
                           updated_at = :assignedAt,
                           updated_by = :operatorId
                     WHERE id = :leadId
                    """,
                    new MapSqlParameterSource()
                            .addValue("operatorId", operatorId)
                            .addValue("assignedAt", databaseTime(assignedAt))
                            .addValue("leadId", leadId)
            );
        } catch (DataIntegrityViolationException exception) {
            throw new CrmPersistenceException("Lead ownership claim could not be persisted", exception);
        }
        return requireLead(leadId);
    }

    private PersistedLead mapPersistedLead(ResultSet resultSet) throws SQLException {
        return new PersistedLead(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("organization_id", UUID.class),
                resultSet.getObject("branch_id", UUID.class),
                resultSet.getString("source_code"),
                resultSet.getObject("source_organization_id", UUID.class),
                resultSet.getString("status_code"),
                resultSet.getObject("status_organization_id", UUID.class),
                resultSet.getString("parent_or_guardian_name"),
                resultSet.getString("normalized_phone"),
                resultSet.getString("display_phone"),
                resultSet.getObject("projected_owner_id", UUID.class),
                resultSet.getObject("active_owner_id", UUID.class),
                resultSet.getObject("lost_reason_id", UUID.class),
                resultSet.getObject("lost_reason_organization_id", UUID.class),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("first_contact_due_at").toInstant()
        );
    }

    private Lead reconstitute(PersistedLead persisted) {
        if (persisted.normalizedPhone() == null || persisted.displayPhone() == null) {
            throw new InconsistentCrmDataException("Lead has no primary phone");
        }
        if (!Objects.equals(persisted.projectedOwnerId(), persisted.activeOwnerId())) {
            throw new InconsistentCrmDataException(
                    "Lead owner projection does not match its active assignment"
            );
        }
        if (!persisted.organizationId().equals(persisted.sourceOrganizationId())
                || !persisted.organizationId().equals(persisted.statusOrganizationId())
                || persisted.lostReasonId() != null
                && !persisted.organizationId().equals(persisted.lostReasonOrganizationId())) {
            throw new InconsistentCrmDataException(
                    "Lead references CRM data from another organization"
            );
        }
        try {
            return Lead.reconstitute(
                    persisted.id(),
                    persisted.organizationId(),
                    persisted.branchId(),
                    LeadSource.valueOf(persisted.sourceCode()),
                    persisted.guardianName(),
                    persisted.displayPhone(),
                    PhoneNumber.of(persisted.normalizedPhone()),
                    LeadStatus.valueOf(persisted.statusCode()),
                    persisted.activeOwnerId(),
                    persisted.lostReasonId(),
                    persisted.createdAt(),
                    persisted.firstContactDueAt()
            );
        } catch (IllegalArgumentException exception) {
            throw new InconsistentCrmDataException(
                    "Persisted lead contains an unsupported source, status, or state"
            );
        }
    }

    private UUID resolveSourceId(UUID organizationId, LeadSource source) {
        return resolveReferenceId(
                "lead_sources", "source", organizationId, source.name()
        );
    }

    private UUID resolveStatusId(UUID organizationId, LeadStatus status) {
        return resolveReferenceId(
                "lead_statuses", "status", organizationId, status.name()
        );
    }

    private UUID resolveInitialStatusId(UUID organizationId) {
        List<UUID> ids = jdbc.queryForList(
                """
                SELECT id
                  FROM lead_statuses
                 WHERE organization_id = :organizationId
                   AND code = 'NEW'
                   AND is_initial
                   AND is_active
                """,
                new MapSqlParameterSource("organizationId", organizationId),
                UUID.class
        );
        if (ids.size() != 1) {
            throw new CrmReferenceDataNotFoundException("initial status", "NEW");
        }
        return ids.getFirst();
    }

    private UUID resolveReferenceId(
            String tableName,
            String referenceType,
            UUID organizationId,
            String code
    ) {
        List<UUID> ids = jdbc.queryForList(
                "SELECT id FROM " + tableName
                        + " WHERE organization_id = :organizationId AND code = :code AND is_active",
                new MapSqlParameterSource()
                        .addValue("organizationId", organizationId)
                        .addValue("code", code),
                UUID.class
        );
        if (ids.size() != 1) {
            throw new CrmReferenceDataNotFoundException(referenceType, code);
        }
        return ids.getFirst();
    }

    private void requireBranchOrganization(UUID branchId, UUID organizationId) {
        Boolean matches = jdbc.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                      FROM branches
                     WHERE id = :branchId
                       AND organization_id = :organizationId
                )
                """,
                new MapSqlParameterSource()
                        .addValue("branchId", branchId)
                        .addValue("organizationId", organizationId),
                Boolean.class
        );
        if (!Boolean.TRUE.equals(matches)) {
            throw new BranchOutsideOrganizationException(branchId, organizationId);
        }
    }

    private void requireUserBranchAccess(
            UUID userId,
            UUID organizationId,
            UUID branchId
    ) {
        Boolean allowed = jdbc.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                      FROM users u
                      JOIN user_branch_access uba ON uba.user_id = u.id
                      JOIN branches b ON b.id = uba.branch_id
                     WHERE u.id = :userId
                       AND u.organization_id = :organizationId
                       AND uba.branch_id = :branchId
                       AND b.organization_id = :organizationId
                )
                """,
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("organizationId", organizationId)
                        .addValue("branchId", branchId),
                Boolean.class
        );
        if (!Boolean.TRUE.equals(allowed)) {
            throw new UserBranchAccessDeniedException();
        }
    }

    private void requireLostReasonOrganization(UUID lostReasonId, UUID organizationId) {
        Boolean matches = jdbc.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                      FROM lost_reasons
                     WHERE id = :lostReasonId
                       AND organization_id = :organizationId
                       AND is_active
                )
                """,
                new MapSqlParameterSource()
                        .addValue("lostReasonId", lostReasonId)
                        .addValue("organizationId", organizationId),
                Boolean.class
        );
        if (!Boolean.TRUE.equals(matches)) {
            throw new CrmPersistenceException(
                    "Lost reason does not belong to the lead organization"
            );
        }
    }

    private StatusState lockStatusState(UUID leadId) {
        List<LockedLeadStatus> rows = jdbc.query(
                """
                SELECT b.organization_id, l.branch_id, l.status_id
                  FROM leads l
                  JOIN branches b ON b.id = l.branch_id
                 WHERE l.id = :leadId
                   FOR UPDATE OF l
                """,
                new MapSqlParameterSource("leadId", leadId),
                (resultSet, rowNumber) -> new LockedLeadStatus(
                        resultSet.getObject("organization_id", UUID.class),
                        resultSet.getObject("branch_id", UUID.class),
                        resultSet.getObject("status_id", UUID.class)
                )
        );
        if (rows.isEmpty()) {
            throw new LeadNotFoundException(leadId);
        }
        LockedLeadStatus locked = rows.getFirst();
        List<StatusReference> statuses = jdbc.query(
                """
                SELECT organization_id, code
                  FROM lead_statuses
                 WHERE id = :statusId
                """,
                new MapSqlParameterSource("statusId", locked.statusId()),
                (resultSet, rowNumber) -> new StatusReference(
                        resultSet.getObject("organization_id", UUID.class),
                        resultSet.getString("code")
                )
        );
        if (statuses.size() != 1
                || !locked.organizationId().equals(statuses.getFirst().organizationId())) {
            throw new InconsistentCrmDataException(
                    "Lead status does not belong to the lead organization"
            );
        }
        return new StatusState(
                locked.organizationId(),
                locked.branchId(),
                locked.statusId(),
                statuses.getFirst().code()
        );
    }

    private OwnershipState lockOwnershipState(UUID leadId) {
        List<LockedLeadOwner> rows = jdbc.query(
                """
                SELECT b.organization_id,
                       l.branch_id,
                       l.owner_user_id AS projected_owner_id
                  FROM leads l
                  JOIN branches b ON b.id = l.branch_id
                 WHERE l.id = :leadId
                   FOR UPDATE OF l
                """,
                new MapSqlParameterSource("leadId", leadId),
                (resultSet, rowNumber) -> new LockedLeadOwner(
                        resultSet.getObject("organization_id", UUID.class),
                        resultSet.getObject("branch_id", UUID.class),
                        resultSet.getObject("projected_owner_id", UUID.class)
                )
        );
        if (rows.isEmpty()) {
            throw new LeadNotFoundException(leadId);
        }
        LockedLeadOwner lockedLead = rows.getFirst();
        List<UUID> activeOwners = jdbc.queryForList(
                """
                SELECT assigned_user_id
                  FROM lead_assignments
                 WHERE lead_id = :leadId
                   AND ended_at IS NULL
                """,
                new MapSqlParameterSource("leadId", leadId),
                UUID.class
        );
        if (activeOwners.size() > 1) {
            throw new InconsistentCrmDataException("Lead has multiple active assignments");
        }
        return new OwnershipState(
                lockedLead.organizationId(),
                lockedLead.branchId(),
                lockedLead.projectedOwnerId(),
                activeOwners.isEmpty() ? null : activeOwners.getFirst()
        );
    }

    private void assertOwnerProjection(OwnershipState state) {
        if (!Objects.equals(state.projectedOwnerId(), state.activeOwnerId())) {
            throw new InconsistentCrmDataException(
                    "Lead owner projection does not match its active assignment"
            );
        }
    }

    private UUID activeOwnerId(UUID leadId) {
        List<UUID> owners = jdbc.queryForList(
                """
                SELECT assigned_user_id
                  FROM lead_assignments
                 WHERE lead_id = :leadId
                   AND ended_at IS NULL
                """,
                new MapSqlParameterSource("leadId", leadId),
                UUID.class
        );
        if (owners.size() != 1) {
            throw new InconsistentCrmDataException(
                    "Lead ownership conflict did not produce one active assignment"
            );
        }
        return owners.getFirst();
    }

    private Lead requireLead(UUID leadId) {
        return findById(leadId).orElseThrow(() -> new LeadNotFoundException(leadId));
    }

    private static OffsetDateTime databaseTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record PersistedLead(
            UUID id,
            UUID organizationId,
            UUID branchId,
            String sourceCode,
            UUID sourceOrganizationId,
            String statusCode,
            UUID statusOrganizationId,
            String guardianName,
            String normalizedPhone,
            String displayPhone,
            UUID projectedOwnerId,
            UUID activeOwnerId,
            UUID lostReasonId,
            UUID lostReasonOrganizationId,
            Instant createdAt,
            Instant firstContactDueAt
    ) {}

    private record LockedLeadStatus(UUID organizationId, UUID branchId, UUID statusId) {}

    private record StatusReference(UUID organizationId, String code) {}

    private record StatusState(
            UUID organizationId,
            UUID branchId,
            UUID statusId,
            String statusCode
    ) {}

    private record LockedLeadOwner(
            UUID organizationId,
            UUID branchId,
            UUID projectedOwnerId
    ) {}

    private record OwnershipState(
            UUID organizationId,
            UUID branchId,
            UUID projectedOwnerId,
            UUID activeOwnerId
    ) {}
}
