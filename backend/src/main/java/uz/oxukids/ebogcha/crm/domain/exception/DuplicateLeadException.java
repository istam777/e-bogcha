package uz.oxukids.ebogcha.crm.domain.exception;

public final class DuplicateLeadException extends RuntimeException {

    public DuplicateLeadException() {
        super("A lead with the same normalized phone number already exists in the organization");
    }
}
