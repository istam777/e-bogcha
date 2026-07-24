package uz.oxukids.ebogcha.auth.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
        Jwt jwt,
        Lockout lockout,
        Cookie cookie,
        DevActor devActor
) {
    public record Jwt(
            String secretBase64,
            int accessExpirationSeconds,
            int refreshExpirationSeconds,
            String issuer,
            String audience
    ) {}

    public record Lockout(
            int maxFailedAttempts,
            int lockDurationSeconds
    ) {
        public Duration lockDuration() {
            return Duration.ofSeconds(lockDurationSeconds);
        }
    }

    public record Cookie(boolean secure) {}

    public record DevActor(boolean headerEnabled) {}
}
