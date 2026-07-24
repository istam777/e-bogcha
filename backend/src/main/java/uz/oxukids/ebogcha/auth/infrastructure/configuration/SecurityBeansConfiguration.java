package uz.oxukids.ebogcha.auth.infrastructure.configuration;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import uz.oxukids.ebogcha.auth.application.port.out.AuthUserRepository;
import uz.oxukids.ebogcha.auth.application.port.out.PrincipalRepository;
import uz.oxukids.ebogcha.auth.infrastructure.web.DevActorFilter;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityBeansConfiguration {

    @Bean
    DevActorFilter devActorFilter(
            AuthUserRepository authUserRepository,
            PrincipalRepository principalRepository,
            @Value("${auth.dev-actor.header-enabled:false}") boolean headerEnabled,
            @Value("${app.env:development}") String appEnv
    ) {
        return new DevActorFilter(authUserRepository, principalRepository, headerEnabled, appEnv);
    }

    @Bean
    @org.springframework.core.annotation.Order(1)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, DevActorFilter devActorFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                            response.getWriter().write(problemDetail(
                                    HttpStatus.UNAUTHORIZED.value(),
                                    "AUTH_UNAUTHENTICATED",
                                    "Unauthenticated",
                                    "Authentication is required.",
                                    request.getRequestURI()
                            ));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                            response.getWriter().write(problemDetail(
                                    HttpStatus.FORBIDDEN.value(),
                                    "AUTH_ACCESS_DENIED",
                                    "Access denied",
                                    "You do not have permission to access this resource.",
                                    request.getRequestURI()
                            ));
                        })
                )
                .addFilterBefore(devActorFilter, org.springframework.security.web.context.SecurityContextHolderFilter.class);

        return http.build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var claims = jwt.getClaims();
            var permissions = claims.get("permissions");
            if (permissions instanceof java.util.List<?> perms) {
                java.util.List<org.springframework.security.core.GrantedAuthority> authorities =
                        new java.util.ArrayList<>();
                for (Object p : perms) {
                    if (p instanceof String perm) {
                        authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority(perm));
                    }
                }
                return authorities;
            }
            return java.util.Collections.emptyList();
        });
        return converter;
    }

    private static String problemDetail(int status, String code, String title, String detail, String instance) {
        return String.format(
                """
                        {"type":"urn:problem:%s","title":"%s","status":%d,"detail":"%s","code":"%s","instance":"%s","timestamp":"%s"}""",
                code.toLowerCase().replace('_', '-'),
                title,
                status,
                detail,
                code,
                instance,
                Instant.now().toString()
        );
    }
}
