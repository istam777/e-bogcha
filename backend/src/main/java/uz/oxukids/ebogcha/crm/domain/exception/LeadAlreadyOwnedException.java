package uz.oxukids.ebogcha.crm.domain.exception;

import java.util.UUID;

public final class LeadAlreadyOwnedException extends RuntimeException {

    public LeadAlreadyOwnedException(UUID leadId) {
        super("Lead is already owned by another operator: " + leadId);
    }
}
