package uz.oxukids.ebogcha.auth.application.port.out;

import java.util.UUID;

public interface PrincipalRepository {
    uz.oxukids.ebogcha.auth.domain.principal.AuthenticatedPrincipal loadPrincipal(UUID userId);
}
