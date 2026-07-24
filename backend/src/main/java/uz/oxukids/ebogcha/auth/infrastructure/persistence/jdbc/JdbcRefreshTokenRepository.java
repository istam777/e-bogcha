package uz.oxukids.ebogcha.auth.infrastructure.persistence.jdbc;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import uz.oxukids.ebogcha.auth.application.port.out.RefreshTokenRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcRefreshTokenRepository implements RefreshTokenRepository {

    private final NamedParameterJdbcTemplate jdbc;

    JdbcRefreshTokenRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(UUID id, UUID userId, String tokenHash, Instant expiresAt, String createdIp, String userAgent) {
        var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("userId", userId)
                .addValue("tokenHash", tokenHash)
                .addValue("expiresAt", Timestamp.from(expiresAt))
                .addValue("createdAt", Timestamp.from(Instant.now()))
                .addValue("createdIp", createdIp)
                .addValue("userAgent", userAgent);
        jdbc.update("""
                INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at, created_at, created_ip, user_agent)
                VALUES (:id, :userId, :tokenHash, :expiresAt, :createdAt, :createdIp, :userAgent)
                """, params);
    }

    @Override
    public Optional<RefreshTokenRecord> findByTokenHashForUpdate(String tokenHash) {
        var params = new MapSqlParameterSource("tokenHash", tokenHash);
        var results = jdbc.query("""
                SELECT id, user_id, token_hash, expires_at, revoked_at
                FROM refresh_tokens WHERE token_hash = :tokenHash FOR UPDATE
                """, params, (rs, rowNum) -> new RefreshTokenRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("token_hash"),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("revoked_at") != null ? rs.getTimestamp("revoked_at").toInstant() : null
        ));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public void revokeById(UUID tokenId, Instant revokedAt) {
        var params = new MapSqlParameterSource()
                .addValue("tokenId", tokenId)
                .addValue("revokedAt", Timestamp.from(revokedAt));
        jdbc.update("UPDATE refresh_tokens SET revoked_at = :revokedAt WHERE id = :tokenId", params);
    }

    @Override
    public void revokeAllActiveByUserId(UUID userId, Instant revokedAt) {
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("revokedAt", Timestamp.from(revokedAt));
        jdbc.update("UPDATE refresh_tokens SET revoked_at = :revokedAt WHERE user_id = :userId AND revoked_at IS NULL", params);
    }
}
