package uz.oxukids.ebogcha.auth.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AuthUser(
        UUID id,
        UUID organizationId,
        String username,
        String displayName,
        boolean organizationActive,
        boolean userActive,
        String statusCode,
        Instant lastLoginAt
) {
    public AuthUser {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(statusCode, "statusCode must not be null");
    }

    public boolean isAuthenticationEligible() {
        return organizationActive && userActive && "ACTIVE".equals(statusCode);
    }
}
