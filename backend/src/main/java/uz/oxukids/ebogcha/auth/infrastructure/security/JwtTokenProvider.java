package uz.oxukids.ebogcha.auth.infrastructure.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;
import uz.oxukids.ebogcha.auth.domain.principal.AuthenticatedPrincipal;
import uz.oxukids.ebogcha.auth.infrastructure.configuration.AuthProperties;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final byte[] signingKey;
    private final String issuer;
    private final String audience;
    private final int accessExpirationSeconds;

    public JwtTokenProvider(AuthProperties authProperties) {
        String base64Secret = authProperties.jwt().secretBase64();
        if (base64Secret == null || base64Secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET_BASE64 must be configured");
        }
        this.signingKey = java.util.Base64.getDecoder().decode(base64Secret);
        if (this.signingKey.length < 32) {
            throw new IllegalStateException(
                    "JWT secret must decode to at least 32 bytes, got " + this.signingKey.length);
        }
        this.issuer = authProperties.jwt().issuer();
        this.audience = authProperties.jwt().audience();
        this.accessExpirationSeconds = authProperties.jwt().accessExpirationSeconds();
    }

    public String createAccessToken(AuthenticatedPrincipal principal) {
        try {
            Instant now = Instant.now();

            List<String> roleCodes = principal.roles().stream()
                    .map(AuthenticatedPrincipal.RoleData::code)
                    .sorted()
                    .toList();

            List<String> permissions = principal.permissions().stream()
                    .sorted()
                    .toList();

            List<String> branchIds = principal.branchIds().stream()
                    .map(UUID::toString)
                    .sorted()
                    .toList();

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(principal.id().toString())
                    .issuer(issuer)
                    .audience(audience)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(accessExpirationSeconds)))
                    .jwtID(UUID.randomUUID().toString())
                    .claim("organization_id", principal.organizationId().toString())
                    .claim("username", principal.username())
                    .claim("display_name", principal.displayName())
                    .claim("roles", roleCodes)
                    .claim("permissions", permissions)
                    .claim("branch_ids", branchIds)
                    .build();

            JWSHeader header = new JWSHeader(JWSAlgorithm.HS256);
            SignedJWT signedJWT = new SignedJWT(header, claims);
            signedJWT.sign(new MACSigner(signingKey));
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create access token", e);
        }
    }
}
