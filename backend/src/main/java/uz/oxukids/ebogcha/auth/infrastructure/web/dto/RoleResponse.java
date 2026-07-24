package uz.oxukids.ebogcha.auth.infrastructure.web.dto;

import java.util.UUID;

public record RoleResponse(
        String code,
        UUID branchId
) {
}
