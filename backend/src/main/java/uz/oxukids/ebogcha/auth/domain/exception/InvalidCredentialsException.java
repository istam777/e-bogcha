package uz.oxukids.ebogcha.auth.domain.exception;

public final class InvalidCredentialsException extends AuthenticationException {

    public InvalidCredentialsException() {
        super("Invalid credentials");
    }
}
