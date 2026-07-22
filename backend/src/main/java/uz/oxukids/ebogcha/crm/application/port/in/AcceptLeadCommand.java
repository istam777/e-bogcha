package uz.oxukids.ebogcha.crm.application.port.in;

import java.util.Objects;
import java.util.UUID;

public record AcceptLeadCommand(UUID leadId, UUID operatorId) {
    public AcceptLeadCommand {
        Objects.requireNonNull(leadId, "leadId must not be null");
        Objects.requireNonNull(operatorId, "operatorId must not be null");
    }
}
