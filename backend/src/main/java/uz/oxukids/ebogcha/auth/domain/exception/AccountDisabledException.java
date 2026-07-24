package uz.oxukids.ebogcha.auth.domain.exception;

public final class AccountDisabledException extends AuthenticationException {

    public AccountDisabledException(String message) {
        super(message);
    }
}
