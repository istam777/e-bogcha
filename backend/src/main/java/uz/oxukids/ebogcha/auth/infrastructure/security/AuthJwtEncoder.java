package uz.oxukids.ebogcha.auth.infrastructure.security;

import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;
import uz.oxukids.ebogcha.auth.application.port.out.Clock;
import uz.oxukids.ebogcha.auth.domain.principal.AuthenticatedPrincipal;
import uz.oxukids.ebogcha.auth.infrastructure.configuration.AuthProperties;

import java.time.Instant;
import java.util.UUID;

@Component
public class AuthJwtEncoder {

    private final JwtEncoder jwtEncoder;
    private final AuthProperties authProperties;
    private final Clock clock;

    public AuthJwtEncoder(JwtEncoder jwtEncoder, AuthProperties authProperties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.authProperties = authProperties;
        this.clock = clock;
    }

    public String encode(AuthenticatedPrincipal principal) {
        Instant now = clock.now();
        Instant expiresAt = now.plusSeconds(authProperties.jwt().accessExpirationSeconds());

        var claimSet = JwtClaimsSet.builder()
                .subject(principal.id().toString())
                .issuer(authProperties.jwt().issuer())
                .audience(java.util.List.of(authProperties.jwt().audience()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("organization_id", principal.organizationId().toString())
                .claim("username", principal.username())
                .claim("display_name", principal.displayName())
                .claim("roles", principal.roles().stream()
                        .map(AuthenticatedPrincipal.RoleData::code).sorted().toList())
                .claim("permissions", principal.permissions().stream().sorted().toList())
                .claim("branch_ids", principal.branchIds().stream()
                        .map(UUID::toString).sorted().toList())
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claimSet)).getTokenValue();
    }
}
