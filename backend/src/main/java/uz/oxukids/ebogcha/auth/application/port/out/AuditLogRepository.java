package uz.oxukids.ebogcha.auth.application.port.out;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public interface AuditLogRepository {
    void write(
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
    );
}
