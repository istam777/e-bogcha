package uz.oxukids.ebogcha.crm.application.port.in;

import uz.oxukids.ebogcha.crm.domain.model.LeadSource;

import java.util.Objects;
import java.util.UUID;

public record CreateLeadCommand(
        UUID leadId,
        UUID organizationId,
        LeadSource source,
        String phoneNumber
) {
    public CreateLeadCommand {
        Objects.requireNonNull(leadId, "leadId must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(phoneNumber, "phoneNumber must not be null");
    }
}
