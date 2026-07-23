package uz.oxukids.ebogcha.crm.application.exception;

public final class LeadSearchBranchAccessDeniedException extends RuntimeException {

    public LeadSearchBranchAccessDeniedException() {
        super("The actor does not have access to the requested branch");
    }
}
