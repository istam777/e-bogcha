package uz.oxukids.ebogcha.auth.application.exception;

public final class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException() {
        super("Authentication is required");
    }
}
