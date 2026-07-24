package uz.oxukids.ebogcha.auth.domain.principal;

import java.util.List;
import java.util.UUID;

public record AuthenticatedPrincipal(
        UUID id,
        UUID organizationId,
        String username,
        String displayName,
        List<RoleData> roles,
        List<String> permissions,
        List<UUID> branchIds
) {
    public record RoleData(String code, UUID branchId) {
    }
}
