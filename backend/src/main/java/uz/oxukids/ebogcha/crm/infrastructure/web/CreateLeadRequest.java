package uz.oxukids.ebogcha.crm.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.oxukids.ebogcha.crm.domain.model.LeadSource;

import java.util.UUID;

public record CreateLeadRequest(
        @NotNull UUID leadId,
        @NotNull UUID organizationId,
        @NotNull UUID branchId,
        @NotNull LeadSource source,
        @NotBlank @Size(max = 255) String parentOrGuardianName,
        @NotBlank @Size(max = 50) String displayPhone
) {
}
