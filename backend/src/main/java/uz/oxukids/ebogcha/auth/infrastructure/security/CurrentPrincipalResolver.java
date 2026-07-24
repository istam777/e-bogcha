package uz.oxukids.ebogcha.auth.infrastructure.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import uz.oxukids.ebogcha.auth.domain.principal.AuthenticatedPrincipal;

import java.util.UUID;

@Component
public class CurrentPrincipalResolver {

    public UUID resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }

        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            String subject = jwt.getSubject();
            if (subject == null || subject.isBlank()) {
                return null;
            }
            try {
                return UUID.fromString(subject);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        Object principal = auth.getPrincipal();
        if (principal instanceof AuthenticatedPrincipal ap) {
            return ap.id();
        }

        return null;
    }

    public AuthenticatedPrincipal resolvePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }

        Object principal = auth.getPrincipal();
        if (principal instanceof AuthenticatedPrincipal ap) {
            return ap;
        }

        return null;
    }
}
