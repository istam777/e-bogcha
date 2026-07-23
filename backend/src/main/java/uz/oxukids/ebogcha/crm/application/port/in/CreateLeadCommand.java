package uz.oxukids.ebogcha.crm.application.port.in;

import uz.oxukids.ebogcha.crm.domain.model.LeadSource;

import java.util.Objects;
import java.util.UUID;

public record CreateLeadCommand(
        UUID leadId,
        UUID organizationId,
        UUID branchId,
        LeadSource source,
        String parentOrGuardianName,
        String displayPhone
) {
    public CreateLeadCommand {
        Objects.requireNonNull(leadId, "leadId must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(branchId, "branchId must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(parentOrGuardianName, "parentOrGuardianName must not be null");
        if (parentOrGuardianName.isBlank()) {
            throw new IllegalArgumentException("parentOrGuardianName must not be blank");
        }
        Objects.requireNonNull(displayPhone, "displayPhone must not be null");
    }

    @Override
    public String toString() {
        return "CreateLeadCommand[leadId=%s, organizationId=%s, branchId=%s, source=%s]"
                .formatted(leadId, organizationId, branchId, source);
    }
}
