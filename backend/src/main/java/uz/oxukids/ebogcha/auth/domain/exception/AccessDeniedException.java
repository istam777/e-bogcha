package uz.oxukids.ebogcha.auth.domain.exception;

public final class AccessDeniedException extends AuthenticationException {

    public AccessDeniedException(String message) {
        super(message);
    }
}
