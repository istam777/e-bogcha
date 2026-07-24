package uz.oxukids.ebogcha.auth.infrastructure.persistence.jdbc;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import uz.oxukids.ebogcha.auth.application.port.out.AuthUserRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcAuthUserRepository implements AuthUserRepository {

    private final NamedParameterJdbcTemplate jdbc;

    JdbcAuthUserRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AuthUserRecord> findById(UUID id) {
        var params = new MapSqlParameterSource("id", id);
        var results = jdbc.query("""
                SELECT u.id, u.organization_id, u.username_normalized, u.display_name,
                       o.is_active AS org_active, u.is_active AS user_active,
                       us.code AS status_code, u.last_login_at
                FROM users u
                JOIN organizations o ON o.id = u.organization_id
                JOIN user_statuses us ON us.id = u.status_id
                WHERE u.id = :id
                """, params, (rs, rowNum) -> new AuthUserRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getString("username_normalized"),
                rs.getString("display_name"),
                rs.getBoolean("org_active"),
                rs.getBoolean("user_active"),
                rs.getString("status_code"),
                rs.getTimestamp("last_login_at") != null ? rs.getTimestamp("last_login_at").toInstant() : null
        ));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Optional<AuthUserRecord> findByNormalizedUsername(String normalizedUsername) {
        var params = new MapSqlParameterSource("username", normalizedUsername);
        var results = jdbc.query("""
                SELECT u.id, u.organization_id, u.username_normalized, u.display_name,
                       o.is_active AS org_active, u.is_active AS user_active,
                       us.code AS status_code, u.last_login_at
                FROM users u
                JOIN organizations o ON o.id = u.organization_id
                JOIN user_statuses us ON us.id = u.status_id
                WHERE u.username_normalized = :username
                """, params, (rs, rowNum) -> new AuthUserRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getString("username_normalized"),
                rs.getString("display_name"),
                rs.getBoolean("org_active"),
                rs.getBoolean("user_active"),
                rs.getString("status_code"),
                rs.getTimestamp("last_login_at") != null ? rs.getTimestamp("last_login_at").toInstant() : null
        ));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Optional<String> findPasswordHashByUserId(UUID userId) {
        var params = new MapSqlParameterSource("userId", userId);
        return jdbc.queryForList(
                "SELECT password_hash FROM user_credentials WHERE user_id = :userId",
                params, String.class
        ).stream().findFirst();
    }

    @Override
    public int countByUsernameAcrossOrganizations(String normalizedUsername) {
        var params = new MapSqlParameterSource("username", normalizedUsername);
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT u.organization_id)
                FROM users u
                JOIN organizations o ON o.id = u.organization_id
                WHERE u.username_normalized = :username
                """, params, Integer.class);
        return count != null ? count : 0;
    }

    @Override
    public Optional<CredentialRecord> findCredentialsByUserIdForUpdate(UUID userId) {
        var params = new MapSqlParameterSource("userId", userId);
        var results = jdbc.query("""
                SELECT password_hash, failed_login_attempts, locked_until
                FROM user_credentials WHERE user_id = :userId FOR UPDATE
                """, params, (rs, rowNum) -> new CredentialRecord(
                rs.getString("password_hash"),
                rs.getInt("failed_login_attempts"),
                rs.getTimestamp("locked_until") != null ? rs.getTimestamp("locked_until").toInstant() : null
        ));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public void incrementFailedLoginAttempts(UUID userId, int newCount, Instant lockedUntil) {
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("count", newCount)
                .addValue("lockedUntil", lockedUntil != null ? Timestamp.from(lockedUntil) : null);
        jdbc.update("""
                UPDATE user_credentials
                SET failed_login_attempts = :count, locked_until = :lockedUntil
                WHERE user_id = :userId
                """, params);
    }

    @Override
    public void resetFailedLoginAttempts(UUID userId, Instant now) {
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("now", Timestamp.from(now));
        jdbc.update("""
                UPDATE user_credentials
                SET failed_login_attempts = 0, locked_until = NULL
                WHERE user_id = :userId
                """, params);
    }

    @Override
    public void updateLastLoginAt(UUID userId, Instant now) {
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("now", Timestamp.from(now));
        jdbc.update("UPDATE users SET last_login_at = :now WHERE id = :userId", params);
    }
}
