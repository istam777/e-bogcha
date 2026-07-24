package uz.oxukids.ebogcha.auth.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {
    void save(UUID id, UUID userId, String tokenHash, Instant expiresAt, String createdIp, String userAgent);
    Optional<RefreshTokenRecord> findByTokenHash(String tokenHash);
    void revokeById(UUID tokenId);
    void revokeAllActiveByUserId(UUID userId);

    record RefreshTokenRecord(UUID id, UUID userId, String tokenHash, Instant expiresAt, Instant revokedAt) {
        public boolean isExpired(Instant now) { return now.isAfter(expiresAt); }
        public boolean isRevoked() { return revokedAt != null; }
    }
}
