package uz.oxukids.ebogcha.crm.application.port.in;

import uz.oxukids.ebogcha.crm.domain.model.LeadStatus;

import java.util.Objects;
import java.util.UUID;

public record ChangeLeadStatusCommand(
        UUID leadId,
        LeadStatus targetStatus,
        UUID lostReasonId,
        UUID changedByUserId
) {
    public ChangeLeadStatusCommand {
        Objects.requireNonNull(leadId, "leadId must not be null");
        Objects.requireNonNull(targetStatus, "targetStatus must not be null");
        Objects.requireNonNull(changedByUserId, "changedByUserId must not be null");
    }
}
