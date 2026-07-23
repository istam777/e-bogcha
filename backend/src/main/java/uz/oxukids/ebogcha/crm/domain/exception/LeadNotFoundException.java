package uz.oxukids.ebogcha.crm.domain.exception;

import java.util.UUID;

public final class LeadNotFoundException extends RuntimeException {

    public LeadNotFoundException(UUID leadId) {
        super("Lead was not found: " + leadId);
    }
}
