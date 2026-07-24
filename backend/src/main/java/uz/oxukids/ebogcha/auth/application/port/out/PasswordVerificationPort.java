package uz.oxukids.ebogcha.auth.application.port.out;

@FunctionalInterface
public interface PasswordVerificationPort {
    boolean matches(String rawPassword, String encodedPassword);
}
