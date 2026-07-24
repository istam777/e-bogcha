package uz.oxukids.ebogcha.auth.infrastructure.web.dto;

import java.util.List;
import java.util.UUID;

public record CurrentUserResponse(
        UUID id,
        UUID organizationId,
        String username,
        String displayName,
        List<RoleResponse> roles,
        List<String> permissions,
        List<UUID> branchIds
) {
}
