package uz.oxukids.ebogcha.auth.application.port.out;

import uz.oxukids.ebogcha.auth.domain.principal.AuthenticatedPrincipal;

import java.util.UUID;

public interface PrincipalRepository {
    AuthenticatedPrincipal loadPrincipal(UUID userId);
}
