package uz.oxukids.ebogcha.auth.infrastructure.service;

import org.springframework.stereotype.Service;
import uz.oxukids.ebogcha.auth.application.port.out.PrincipalRepository;
import uz.oxukids.ebogcha.auth.application.service.GetCurrentUserService;
import uz.oxukids.ebogcha.auth.domain.principal.AuthenticatedPrincipal;
import uz.oxukids.ebogcha.auth.infrastructure.web.dto.CurrentUserResponse;
import uz.oxukids.ebogcha.auth.infrastructure.web.dto.RoleResponse;

import java.util.UUID;

@Service
class GetCurrentUserServiceImpl implements GetCurrentUserService {

    private final PrincipalRepository principalRepository;

    GetCurrentUserServiceImpl(PrincipalRepository principalRepository) {
        this.principalRepository = principalRepository;
    }

    @Override
    public CurrentUserResponse getCurrentUser(UUID userId) {
        AuthenticatedPrincipal principal = principalRepository.loadPrincipal(userId);
        var roles = principal.roles().stream()
                .map(r -> new RoleResponse(r.code(), r.branchId()))
                .toList();
        return new CurrentUserResponse(
                principal.id(), principal.organizationId(),
                principal.username(), principal.displayName(),
                roles, principal.permissions(), principal.branchIds()
        );
    }
}
