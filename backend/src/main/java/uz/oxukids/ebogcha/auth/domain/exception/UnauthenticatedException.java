package uz.oxukids.ebogcha.auth.domain.exception;

public final class UnauthenticatedException extends AuthenticationException {

    public UnauthenticatedException() {
        super("Authentication required");
    }

    public UnauthenticatedException(String message) {
        super(message);
    }
}
