package uz.oxukids.ebogcha.auth.application.exception;

public final class AccountDisabledException extends RuntimeException {

    public AccountDisabledException() {
        super("Account is disabled");
    }
}
