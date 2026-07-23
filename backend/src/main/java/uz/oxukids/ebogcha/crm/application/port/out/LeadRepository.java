package uz.oxukids.ebogcha.crm.application.port.out;

import uz.oxukids.ebogcha.crm.domain.exception.LeadAlreadyOwnedException;
import uz.oxukids.ebogcha.crm.domain.exception.LeadNotFoundException;
import uz.oxukids.ebogcha.crm.domain.model.Lead;
import uz.oxukids.ebogcha.crm.domain.model.LeadStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface LeadRepository {

    Optional<Lead> findById(UUID leadId);

    /**
     * Persists a new lead and its primary phone in one transaction.
     */
    void saveNew(Lead lead);

    /**
     * Persists the current status and appends the matching status-history entry
     * atomically. Status history must not be overwritten.
     */
    void saveStatusChange(
            Lead lead,
            LeadStatus previousStatus,
            Instant changedAt,
            UUID changedByUserId
    );

    /**
     * Claims ownership atomically. Implementations must make the first successful
     * claimant win, treat a repeated claim by that operator as idempotent, and
     * reject a different operator with {@link LeadAlreadyOwnedException}.
     *
     * @throws LeadNotFoundException when the lead does not exist
     */
    Lead claimOwnership(UUID leadId, UUID operatorId);
}
