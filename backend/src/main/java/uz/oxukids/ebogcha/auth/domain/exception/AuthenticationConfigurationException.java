package uz.oxukids.ebogcha.auth.domain.exception;

public final class AuthenticationConfigurationException extends AuthenticationException {

    public AuthenticationConfigurationException(String message) {
        super(message);
    }

    public AuthenticationConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
