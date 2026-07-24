package uz.oxukids.ebogcha.auth.infrastructure.persistence.jdbc;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import uz.oxukids.ebogcha.auth.application.port.out.AuthUserRepository;
import uz.oxukids.ebogcha.auth.application.port.out.AuthUserRecord;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAuthUserRepository implements AuthUserRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAuthUserRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AuthUserRecord> findById(UUID id) {
        var params = new MapSqlParameterSource("id", id);
        var results = jdbc.query("""
                SELECT u.id, u.organization_id, u.username_normalized, u.display_name,
                       u.is_active AS user_active, us.code AS status_code,
                       o.is_active AS org_active, u.last_login_at
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
                rs.getTimestamp("last_login_at") != null
                        ? rs.getTimestamp("last_login_at").toInstant() : null
        ));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Optional<AuthUserRecord> findByNormalizedUsername(String normalizedUsername) {
        var params = new MapSqlParameterSource("username", normalizedUsername);
        var results = jdbc.query("""
                SELECT u.id, u.organization_id, u.username_normalized, u.display_name,
                       u.is_active AS user_active, us.code AS status_code,
                       o.is_active AS org_active, u.last_login_at
                FROM users u
                JOIN organizations o ON o.id = u.organization_id
                JOIN user_statuses us ON us.id = u.status_id
                WHERE u.username_normalized = :username
                AND o.is_active = true AND u.is_active = true AND us.code = 'ACTIVE'
                """, params, (rs, rowNum) -> new AuthUserRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getString("username_normalized"),
                rs.getString("display_name"),
                rs.getBoolean("org_active"),
                rs.getBoolean("user_active"),
                rs.getString("status_code"),
                rs.getTimestamp("last_login_at") != null
                        ? rs.getTimestamp("last_login_at").toInstant() : null
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
    public int countActiveByUsernameAcrossOrganizations(String normalizedUsername) {
        var params = new MapSqlParameterSource("username", normalizedUsername);
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT u.organization_id)
                FROM users u
                JOIN organizations o ON o.id = u.organization_id
                JOIN user_statuses us ON us.id = u.status_id
                WHERE u.username_normalized = :username
                AND o.is_active = true AND u.is_active = true AND us.code = 'ACTIVE'
                """, params, Integer.class);
        return count != null ? count : 0;
    }

    @Override
    public void incrementFailedLoginAttempts(UUID userId, int newCount, Instant lockedUntil) {
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("count", newCount)
                .addValue("lockedUntil", lockedUntil != null
                        ? java.sql.Timestamp.from(lockedUntil) : null);
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
                .addValue("now", java.sql.Timestamp.from(now));
        jdbc.update("""
                UPDATE user_credentials
                SET failed_login_attempts = 0, locked_until = NULL
                WHERE user_id = :userId
                """, params);
        jdbc.update("""
                UPDATE users SET last_login_at = :now WHERE id = :userId
                """, params);
    }

    @Override
    public int findFailedLoginAttempts(UUID userId) {
        var params = new MapSqlParameterSource("userId", userId);
        Integer count = jdbc.queryForObject(
                "SELECT failed_login_attempts FROM user_credentials WHERE user_id = :userId",
                params, Integer.class);
        return count != null ? count : 0;
    }

    @Override
    public Optional<Instant> findLockedUntilByUserId(UUID userId) {
        var params = new MapSqlParameterSource("userId", userId);
        return jdbc.queryForList(
                "SELECT locked_until FROM user_credentials WHERE user_id = :userId",
                params, java.sql.Timestamp.class
        ).stream().findFirst().map(t -> t != null ? t.toInstant() : null);
    }
}
