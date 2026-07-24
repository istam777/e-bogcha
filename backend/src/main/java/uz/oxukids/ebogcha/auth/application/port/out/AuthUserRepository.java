package uz.oxukids.ebogcha.auth.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthUserRepository {
    Optional<AuthUserRecord> findById(UUID id);
    Optional<AuthUserRecord> findByNormalizedUsername(String normalizedUsername);
    Optional<String> findPasswordHashByUserId(UUID userId);
    int countActiveByUsernameAcrossOrganizations(String normalizedUsername);
    int findFailedLoginAttempts(UUID userId);
    Optional<Instant> findLockedUntilByUserId(UUID userId);
    void incrementFailedLoginAttempts(UUID userId, int newCount, Instant lockedUntil);
    void resetFailedLoginAttempts(UUID userId, Instant now);
}
