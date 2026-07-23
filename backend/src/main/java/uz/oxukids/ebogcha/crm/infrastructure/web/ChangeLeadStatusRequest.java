package uz.oxukids.ebogcha.crm.infrastructure.web;

import jakarta.validation.constraints.NotNull;
import uz.oxukids.ebogcha.crm.domain.model.LeadStatus;

import java.util.UUID;

public record ChangeLeadStatusRequest(
        @NotNull LeadStatus targetStatus,
        UUID lostReasonId
) {
}
