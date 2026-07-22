package uz.oxukids.ebogcha.crm.domain.exception;

public final class InvalidPhoneNumberException extends IllegalArgumentException {

    public InvalidPhoneNumberException(String message) {
        super(message);
    }

    public InvalidPhoneNumberException(String message, Throwable cause) {
        super(message, cause);
    }
}
