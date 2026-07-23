package uz.oxukids.ebogcha.crm.domain.model;

import uz.oxukids.ebogcha.crm.domain.exception.InvalidLeadStatusTransitionException;
import uz.oxukids.ebogcha.crm.domain.exception.LeadAlreadyOwnedException;
import uz.oxukids.ebogcha.crm.domain.exception.LostReasonRequiredException;
import uz.oxukids.ebogcha.crm.domain.policy.InitialContactDeadlinePolicy;
import uz.oxukids.ebogcha.crm.domain.policy.LeadStatusTransitionPolicy;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class Lead {

    private final UUID id;
    private final UUID organizationId;
    private final UUID branchId;
    private final LeadSource source;
    private final String parentOrGuardianName;
    private final String primaryDisplayPhone;
    private final PhoneNumber primaryPhone;
    private final Instant createdAt;
    private final Instant firstContactDueAt;
    private LeadStatus status;
    private UUID ownerOperatorId;
    private UUID lostReasonId;

    private Lead(
            UUID id,
            UUID organizationId,
            UUID branchId,
            LeadSource source,
            String parentOrGuardianName,
            String primaryDisplayPhone,
            PhoneNumber primaryPhone,
            LeadStatus status,
            UUID lostReasonId,
            Instant createdAt,
            Instant firstContactDueAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId must not be null");
        this.branchId = Objects.requireNonNull(branchId, "branchId must not be null");
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.parentOrGuardianName = requireNonBlank(
                parentOrGuardianName, "parentOrGuardianName must not be null or blank"
        );
        this.primaryDisplayPhone = requireNonBlank(
                primaryDisplayPhone, "primaryDisplayPhone must not be null or blank"
        );
        this.primaryPhone = Objects.requireNonNull(primaryPhone, "primaryPhone must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        if (status == LeadStatus.LOST && lostReasonId == null) {
            throw new LostReasonRequiredException();
        }
        if (status != LeadStatus.LOST && lostReasonId != null) {
            throw new IllegalArgumentException("lostReasonId is only valid for LOST leads");
        }
        this.lostReasonId = lostReasonId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.firstContactDueAt = Objects.requireNonNull(firstContactDueAt, "firstContactDueAt must not be null");
    }

    public static Lead create(
            UUID id,
            UUID organizationId,
            UUID branchId,
            LeadSource source,
            String parentOrGuardianName,
            String primaryDisplayPhone,
            PhoneNumber primaryPhone,
            Instant creationTime,
            InitialContactDeadlinePolicy deadlinePolicy
    ) {
        Objects.requireNonNull(deadlinePolicy, "deadlinePolicy must not be null");
        return new Lead(
                id, organizationId, branchId, source, parentOrGuardianName, primaryDisplayPhone,
                primaryPhone, LeadStatus.NEW, null, creationTime,
                deadlinePolicy.deadlineFor(creationTime)
        );
    }

    public static Lead reconstitute(
            UUID id,
            UUID organizationId,
            UUID branchId,
            LeadSource source,
            String parentOrGuardianName,
            String primaryDisplayPhone,
            PhoneNumber primaryPhone,
            LeadStatus status,
            UUID ownerOperatorId,
            UUID lostReasonId,
            Instant createdAt,
            Instant firstContactDueAt
    ) {
        Lead lead = new Lead(
                id, organizationId, branchId, source, parentOrGuardianName, primaryDisplayPhone,
                primaryPhone, status, lostReasonId, createdAt, firstContactDueAt
        );
        lead.ownerOperatorId = ownerOperatorId;
        return lead;
    }

    public void accept(UUID operatorId) {
        Objects.requireNonNull(operatorId, "operatorId must not be null");
        if (ownerOperatorId == null) {
            ownerOperatorId = operatorId;
            return;
        }
        if (!ownerOperatorId.equals(operatorId)) {
            throw new LeadAlreadyOwnedException(id);
        }
    }

    public boolean changeStatus(
            LeadStatus target,
            UUID targetLostReasonId,
            LeadStatusTransitionPolicy policy
    ) {
        Objects.requireNonNull(target, "target status must not be null");
        Objects.requireNonNull(policy, "policy must not be null");
        if (!policy.allows(status, target)) {
            throw new InvalidLeadStatusTransitionException(status, target);
        }
        if (status == target) {
            return false;
        }
        if (target == LeadStatus.LOST && targetLostReasonId == null) {
            throw new LostReasonRequiredException();
        }
        status = target;
        lostReasonId = target == LeadStatus.LOST ? targetLostReasonId : null;
        return true;
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    public UUID id() {
        return id;
    }

    public UUID organizationId() {
        return organizationId;
    }

    public UUID branchId() {
        return branchId;
    }

    public LeadSource source() {
        return source;
    }

    public String parentOrGuardianName() {
        return parentOrGuardianName;
    }

    public String primaryDisplayPhone() {
        return primaryDisplayPhone;
    }

    public PhoneNumber primaryPhone() {
        return primaryPhone;
    }

    public LeadStatus status() {
        return status;
    }

    public Optional<UUID> ownerOperatorId() {
        return Optional.ofNullable(ownerOperatorId);
    }

    public Optional<UUID> lostReasonId() {
        return Optional.ofNullable(lostReasonId);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant firstContactDueAt() {
        return firstContactDueAt;
    }
}
