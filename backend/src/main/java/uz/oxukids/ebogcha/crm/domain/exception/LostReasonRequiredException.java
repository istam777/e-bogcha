package uz.oxukids.ebogcha.crm.domain.exception;

public final class LostReasonRequiredException extends IllegalArgumentException {

    public LostReasonRequiredException() {
        super("lostReasonId is required when transitioning a lead to LOST");
    }
}
