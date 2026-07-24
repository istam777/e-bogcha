package uz.oxukids.ebogcha.auth.infrastructure.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.oxukids.ebogcha.auth.application.port.out.*;
import uz.oxukids.ebogcha.auth.application.service.RefreshTokenService;
import uz.oxukids.ebogcha.auth.domain.exception.RefreshTokenException;
import uz.oxukids.ebogcha.auth.domain.exception.RefreshTokenReusedException;
import uz.oxukids.ebogcha.auth.domain.principal.AuthenticatedPrincipal;
import uz.oxukids.ebogcha.auth.infrastructure.configuration.AuthProperties;
import uz.oxukids.ebogcha.auth.infrastructure.security.AuthJwtEncoder;
import uz.oxukids.ebogcha.auth.infrastructure.security.AuthRefreshTokenGenerator;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthUserRepository authUserRepository;
    private final PrincipalRepository principalRepository;
    private final AuthJwtEncoder jwtEncoder;
    private final AuthRefreshTokenGenerator refreshTokenGenerator;
    private final AuditLogRepository auditLogRepository;
    private final AuthProperties authProperties;
    private final Clock clock;

    RefreshTokenServiceImpl(
            RefreshTokenRepository refreshTokenRepository,
            AuthUserRepository authUserRepository,
            PrincipalRepository principalRepository,
            AuthJwtEncoder jwtEncoder,
            AuthRefreshTokenGenerator refreshTokenGenerator,
            AuditLogRepository auditLogRepository,
            AuthProperties authProperties,
            Clock clock
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.authUserRepository = authUserRepository;
        this.principalRepository = principalRepository;
        this.jwtEncoder = jwtEncoder;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.auditLogRepository = auditLogRepository;
        this.authProperties = authProperties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public RefreshResult refresh(String rawRefreshToken, String ip, String userAgent) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new RefreshTokenException("No refresh token provided");
        }

        String tokenHash = refreshTokenGenerator.hashToken(rawRefreshToken);
        Instant now = clock.now();

        var tokenOpt = refreshTokenRepository.findByTokenHashForUpdate(tokenHash);
        if (tokenOpt.isEmpty()) {
            throw new RefreshTokenException("Invalid refresh token");
        }

        var token = tokenOpt.get();

        if (token.isRevoked()) {
            refreshTokenRepository.revokeAllActiveByUserId(token.userId(), now);
            auditLogRepository.write(null, token.userId(), token.userId(),
                    "USER", token.userId(), "AUTH_REFRESH_REUSE_DETECTED",
                    null, ip, userAgent, Map.of());
            throw new RefreshTokenReusedException(token.userId());
        }

        if (token.isExpired(now)) {
            throw new RefreshTokenException("Refresh token expired");
        }

        refreshTokenRepository.revokeById(token.id(), now);

        var userOpt = authUserRepository.findById(token.userId());
        if (userOpt.isEmpty() || !userOpt.get().isAuthenticationEligible()) {
            throw new RefreshTokenException("Account no longer active");
        }

        AuthenticatedPrincipal principal = principalRepository.loadPrincipal(token.userId());
        String newAccessToken = jwtEncoder.encode(principal);
        String newRawRefreshToken = refreshTokenGenerator.generateRawToken();
        String newHash = refreshTokenGenerator.hashToken(newRawRefreshToken);

        refreshTokenRepository.save(UUID.randomUUID(), token.userId(), newHash,
                now.plus(Duration.ofSeconds(authProperties.jwt().refreshExpirationSeconds())),
                ip, userAgent);

        auditLogRepository.write(userOpt.get().organizationId(), token.userId(), token.userId(),
                "USER", token.userId(), "AUTH_REFRESH_SUCCEEDED",
                null, ip, userAgent, Map.of());

        return new RefreshResult(newAccessToken, newRawRefreshToken, token.userId());
    }
}
