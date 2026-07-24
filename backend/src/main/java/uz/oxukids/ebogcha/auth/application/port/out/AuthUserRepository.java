package uz.oxukids.ebogcha.auth.application.port.out;

import java.util.UUID;

public interface AuthUserRepository {
    java.util.Optional<AuthUserRecord> findById(UUID id);
    java.util.Optional<AuthUserRecord> findByNormalizedUsername(String normalizedUsername);
    java.util.Optional<String> findPasswordHashByUserId(UUID userId);
    int countByUsernameAcrossOrganizations(String normalizedUsername);
    java.util.Optional<CredentialRecord> findCredentialsByUserIdForUpdate(UUID userId);
    void incrementFailedLoginAttempts(UUID userId, int newCount, java.time.Instant lockedUntil);
    void resetFailedLoginAttempts(UUID userId, java.time.Instant now);
    void updateLastLoginAt(UUID userId, java.time.Instant now);

    record AuthUserRecord(
            UUID id,
            UUID organizationId,
            String username,
            String displayName,
            boolean organizationActive,
            boolean userActive,
            String statusCode,
            java.time.Instant lastLoginAt
    ) {
        public boolean isAuthenticationEligible() {
            return organizationActive && userActive && "ACTIVE".equals(statusCode);
        }
    }

    record CredentialRecord(
            String passwordHash,
            int failedLoginAttempts,
            java.time.Instant lockedUntil
    ) {
        public boolean isLocked(java.time.Instant now) {
            return lockedUntil != null && now.isBefore(lockedUntil);
        }
    }
}
