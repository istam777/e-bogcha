package uz.oxukids.ebogcha.crm.domain.model;

import uz.oxukids.ebogcha.crm.domain.exception.InvalidLeadStatusTransitionException;
import uz.oxukids.ebogcha.crm.domain.exception.LeadAlreadyOwnedException;
import uz.oxukids.ebogcha.crm.domain.policy.InitialContactDeadlinePolicy;
import uz.oxukids.ebogcha.crm.domain.policy.LeadStatusTransitionPolicy;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class Lead {

    private final UUID id;
    private final UUID organizationId;
    private final LeadSource source;
    private final PhoneNumber primaryPhone;
    private final Instant createdAt;
    private final Instant firstContactDueAt;
    private LeadStatus status;
    private UUID ownerOperatorId;

    private Lead(
            UUID id,
            UUID organizationId,
            LeadSource source,
            PhoneNumber primaryPhone,
            LeadStatus status,
            Instant createdAt,
            Instant firstContactDueAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId must not be null");
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.primaryPhone = Objects.requireNonNull(primaryPhone, "primaryPhone must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.firstContactDueAt = Objects.requireNonNull(firstContactDueAt, "firstContactDueAt must not be null");
    }

    public static Lead create(
            UUID id,
            UUID organizationId,
            LeadSource source,
            PhoneNumber primaryPhone,
            Instant creationTime,
            InitialContactDeadlinePolicy deadlinePolicy
    ) {
        Objects.requireNonNull(deadlinePolicy, "deadlinePolicy must not be null");
        return new Lead(
                id, organizationId, source, primaryPhone, LeadStatus.NEW, creationTime,
                deadlinePolicy.deadlineFor(creationTime)
        );
    }

    public static Lead reconstitute(
            UUID id,
            UUID organizationId,
            LeadSource source,
            PhoneNumber primaryPhone,
            LeadStatus status,
            UUID ownerOperatorId,
            Instant createdAt,
            Instant firstContactDueAt
    ) {
        Lead lead = new Lead(
                id, organizationId, source, primaryPhone, status, createdAt, firstContactDueAt
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

    public void changeStatus(LeadStatus target, LeadStatusTransitionPolicy policy) {
        Objects.requireNonNull(target, "target status must not be null");
        Objects.requireNonNull(policy, "policy must not be null");
        if (!policy.allows(status, target)) {
            throw new InvalidLeadStatusTransitionException(status, target);
        }
        status = target;
    }

    public UUID id() {
        return id;
    }

    public UUID organizationId() {
        return organizationId;
    }

    public LeadSource source() {
        return source;
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

    public Instant createdAt() {
        return createdAt;
    }

    public Instant firstContactDueAt() {
        return firstContactDueAt;
    }
}
