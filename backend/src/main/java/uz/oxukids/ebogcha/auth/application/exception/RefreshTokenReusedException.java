package uz.oxukids.ebogcha.auth.application.exception;

public final class RefreshTokenReusedException extends RuntimeException {

    public RefreshTokenReusedException() {
        super("Refresh token has already been used");
    }
}
