package uz.oxukids.ebogcha.auth.application.port.out;

import java.time.Instant;
import java.util.UUID;

public record AuthUserRecord(
        UUID id,
        UUID organizationId,
        String username,
        String displayName,
        boolean organizationActive,
        boolean userActive,
        String statusCode,
        Instant lastLoginAt
) {
    public boolean isAuthenticationEligible() {
        return organizationActive && userActive && "ACTIVE".equals(statusCode);
    }
}
