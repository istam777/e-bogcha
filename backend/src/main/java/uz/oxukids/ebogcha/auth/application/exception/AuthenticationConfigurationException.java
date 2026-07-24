package uz.oxukids.ebogcha.auth.application.exception;

public final class AuthenticationConfigurationException extends RuntimeException {

    public AuthenticationConfigurationException(String message) {
        super(message);
    }

    public AuthenticationConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
