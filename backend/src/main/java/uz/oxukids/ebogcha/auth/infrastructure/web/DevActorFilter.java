package uz.oxukids.ebogcha.auth.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import uz.oxukids.ebogcha.auth.application.port.out.AuthUserRepository;
import uz.oxukids.ebogcha.auth.application.port.out.PrincipalRepository;
import uz.oxukids.ebogcha.auth.domain.principal.AuthenticatedPrincipal;

import java.io.IOException;
import java.util.List;

@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class DevActorFilter extends OncePerRequestFilter {

    private static final String ACTOR_HEADER = "X-Actor-User-Id";

    private final AuthUserRepository authUserRepository;
    private final PrincipalRepository principalRepository;
    private final boolean headerEnabled;
    private final String appEnv;

    public DevActorFilter(
            AuthUserRepository authUserRepository,
            PrincipalRepository principalRepository,
            @Value("${auth.dev-actor.header-enabled:false}") boolean headerEnabled,
            @Value("${app.env:development}") String appEnv
    ) {
        this.authUserRepository = authUserRepository;
        this.principalRepository = principalRepository;
        this.headerEnabled = headerEnabled;
        this.appEnv = appEnv;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if ("production".equals(appEnv) && headerEnabled) {
            throw new IllegalStateException(
                    "Dev actor header is enabled in production. "
                            + "Set auth.dev-actor.header-enabled=false for production."
            );
        }
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getServletPath();
        if (path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/refresh")) {
            filterChain.doFilter(request, response);
            return;
        }

        String actorHeader = request.getHeader(ACTOR_HEADER);

        if (actorHeader != null && !actorHeader.isBlank()) {
            if (!headerEnabled) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/problem+json");
                response.getWriter().write("""
                        {"type":"urn:problem:auth-dev-actor-disabled","title":"Dev actor disabled","status":403,"detail":"X-Actor-User-Id header is not allowed in this environment.","code":"AUTH_DEV_ACTOR_DISABLED","instance":"%s"}""".formatted(request.getRequestURI()));
                return;
            }

            java.util.UUID userId;
            try {
                userId = java.util.UUID.fromString(actorHeader);
            } catch (IllegalArgumentException e) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/problem+json");
                response.getWriter().write("""
                        {"type":"urn:problem:auth-dev-actor-invalid","title":"Invalid dev actor","status":403,"detail":"X-Actor-User-Id must contain a valid UUID.","code":"AUTH_DEV_ACTOR_INVALID","instance":"%s"}""".formatted(request.getRequestURI()));
                return;
            }

            // Verify user exists and is active
            var userOpt = authUserRepository.findById(userId);
            if (userOpt.isEmpty() || !userOpt.get().isAuthenticationEligible()) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/problem+json");
                response.getWriter().write("""
                        {"type":"urn:problem:auth-dev-actor-invalid","title":"Invalid dev actor","status":403,"detail":"The specified user does not exist or is not active.","code":"AUTH_DEV_ACTOR_INVALID","instance":"%s"}""".formatted(request.getRequestURI()));
                return;
            }

            AuthenticatedPrincipal principal = principalRepository.loadPrincipal(userId);

            var authorities = principal.permissions().stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
