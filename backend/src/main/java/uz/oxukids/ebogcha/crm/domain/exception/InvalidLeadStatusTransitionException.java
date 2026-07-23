package uz.oxukids.ebogcha.crm.domain.exception;

import uz.oxukids.ebogcha.crm.domain.model.LeadStatus;

public final class InvalidLeadStatusTransitionException extends RuntimeException {

    public InvalidLeadStatusTransitionException(LeadStatus current, LeadStatus target) {
        super("Lead status transition is not allowed: " + current + " -> " + target);
    }
}
