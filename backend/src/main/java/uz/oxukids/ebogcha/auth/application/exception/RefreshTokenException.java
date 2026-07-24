package uz.oxukids.ebogcha.auth.application.exception;

public final class RefreshTokenException extends RuntimeException {

    public RefreshTokenException(String message) {
        super(message);
    }

    public RefreshTokenException() {
        super("Invalid or expired refresh token");
    }
}
