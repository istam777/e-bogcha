package uz.oxukids.ebogcha.auth.infrastructure.persistence.jdbc;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import uz.oxukids.ebogcha.auth.application.port.out.PrincipalRepository;
import uz.oxukids.ebogcha.auth.domain.principal.AuthenticatedPrincipal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcPrincipalRepository implements PrincipalRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcPrincipalRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AuthenticatedPrincipal loadPrincipal(UUID userId) {
        Instant now = Instant.now();
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("now", java.sql.Timestamp.from(now));

        // Load user info
        var userData = jdbc.queryForObject("""
                SELECT u.id, u.organization_id, u.username_normalized, u.display_name
                FROM users u WHERE u.id = :userId
                """, params, (rs, rowNum) -> new Object[]{
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getString("username_normalized"),
                rs.getString("display_name")
        });

        if (userData == null) {
            throw new org.springframework.security.core.userdetails.UsernameNotFoundException("User not found");
        }

        UUID id = (UUID) userData[0];
        UUID orgId = (UUID) userData[1];
        String username = (String) userData[2];
        String displayName = (String) userData[3];

        // Load roles
        var roleParams = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("now", java.sql.Timestamp.from(now))
                .addValue("orgId", orgId);

        var roles = jdbc.query("""
                SELECT r.code, ur.branch_id
                FROM user_roles ur
                JOIN roles r ON r.id = ur.role_id
                WHERE ur.user_id = :userId
                AND r.organization_id = :orgId
                AND r.is_active = true
                AND ur.valid_from <= :now
                AND (ur.valid_until IS NULL OR ur.valid_until > :now)
                """, roleParams, (rs, rowNum) -> new AuthenticatedPrincipal.RoleData(
                rs.getString("code"),
                rs.getObject("branch_id", UUID.class)
        ));

        // Load permissions
        var permissions = jdbc.query("""
                SELECT DISTINCT p.code
                FROM user_roles ur
                JOIN roles r ON r.id = ur.role_id AND r.organization_id = :orgId
                JOIN role_permissions rp ON rp.role_id = r.id
                JOIN permissions p ON p.id = rp.permission_id
                WHERE ur.user_id = :userId
                AND r.is_active = true
                AND ur.valid_from <= :now
                AND (ur.valid_until IS NULL OR ur.valid_until > :now)
                """, roleParams, (rs, rowNum) -> rs.getString("code"));

        // Load branch access
        var branchParams = new MapSqlParameterSource("userId", userId);
        var branchIds = jdbc.queryForList("""
                SELECT branch_id FROM user_branch_access WHERE user_id = :userId
                """, branchParams, UUID.class);

        return new AuthenticatedPrincipal(
                id, orgId, username, displayName,
                List.copyOf(roles),
                List.copyOf(permissions),
                List.copyOf(branchIds)
        );
    }
}
