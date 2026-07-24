package uz.oxukids.ebogcha.auth.infrastructure.persistence.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import uz.oxukids.ebogcha.auth.application.port.out.AuditLogRepository;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Repository
public class JdbcAuditLogRepository implements AuditLogRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcAuditLogRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void write(
            UUID organizationId,
            UUID userId,
            UUID actorUserId,
            String entityType,
            UUID entityId,
            String action,
            String correlationId,
            String ipAddress,
            String userAgent,
            Map<String, Object> sanitizedMetadata
    ) {
        String metadataJson = null;
        if (sanitizedMetadata != null && !sanitizedMetadata.isEmpty()) {
            try {
                metadataJson = objectMapper.writeValueAsString(sanitizedMetadata);
            } catch (Exception e) {
                metadataJson = "{}";
            }
        }

        var params = new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("organizationId", organizationId)
                .addValue("actorUserId", actorUserId)
                .addValue("entityType", entityType)
                .addValue("entityId", entityId)
                .addValue("action", action)
                .addValue("correlationId", correlationId)
                .addValue("ipAddress", ipAddress)
                .addValue("userAgent", userAgent)
                .addValue("createdAt", java.sql.Timestamp.from(Instant.now()))
                .addValue("sanitizedMetadata", metadataJson);

        jdbc.update("""
                INSERT INTO audit_logs (id, organization_id, actor_user_id, entity_type, entity_id, action,
                    correlation_id, sanitized_metadata, ip_address, user_agent, created_at)
                VALUES (:id, :organizationId, :actorUserId, :entityType, :entityId, :action,
                    :correlationId, CAST(:sanitizedMetadata AS jsonb), :ipAddress, :userAgent, :createdAt)
                """, params);
    }
}
