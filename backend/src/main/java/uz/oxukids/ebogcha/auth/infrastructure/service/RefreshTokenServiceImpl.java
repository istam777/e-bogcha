package uz.oxukids.ebogcha.auth.infrastructure.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.oxukids.ebogcha.auth.application.exception.RefreshTokenException;
import uz.oxukids.ebogcha.auth.application.exception.RefreshTokenReusedException;
import uz.oxukids.ebogcha.auth.application.port.out.AuditLogRepository;
import uz.oxukids.ebogcha.auth.application.port.out.PrincipalRepository;
import uz.oxukids.ebogcha.auth.application.port.out.RefreshTokenRepository;
import uz.oxukids.ebogcha.auth.application.service.RefreshTokenService;
import uz.oxukids.ebogcha.auth.infrastructure.security.JwtTokenProvider;
import uz.oxukids.ebogcha.auth.infrastructure.security.RefreshTokenGenerator;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final PrincipalRepository principalRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final AuditLogRepository auditLogRepository;

    public RefreshTokenServiceImpl(
            RefreshTokenRepository refreshTokenRepository,
            PrincipalRepository principalRepository,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenGenerator refreshTokenGenerator,
            AuditLogRepository auditLogRepository
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.principalRepository = principalRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    @Transactional
    public RefreshResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new RefreshTokenException("No refresh token provided");
        }

        String tokenHash = refreshTokenGenerator.hashToken(rawRefreshToken);
        Instant now = Instant.now();

        var tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);
        if (tokenOpt.isEmpty()) {
            throw new RefreshTokenException("Invalid refresh token");
        }

        var token = tokenOpt.get();

        if (token.isRevoked()) {
            // Reuse detection
            refreshTokenRepository.revokeAllActiveByUserId(token.userId());
            auditLogRepository.write(null, token.userId(), token.userId(),
                    "USER", token.userId(), "AUTH_REFRESH_REUSE_DETECTED",
                    null, null, null, Map.of());
            throw new RefreshTokenReusedException();
        }

        if (token.isExpired(now)) {
            throw new RefreshTokenException("Refresh token expired");
        }

        // Revoke old token
        refreshTokenRepository.revokeById(token.id());

        // Generate new tokens
        var principal = principalRepository.loadPrincipal(token.userId());
        String newAccessToken = jwtTokenProvider.createAccessToken(principal);
        String newRawRefreshToken = refreshTokenGenerator.generateRawToken();
        String newHash = refreshTokenGenerator.hashToken(newRawRefreshToken);
        UUID newRefreshId = UUID.randomUUID();

        refreshTokenRepository.save(newRefreshId, token.userId(), newHash,
                now.plus(Duration.ofDays(30)), null, null);

        auditLogRepository.write(null, token.userId(), token.userId(),
                "USER", token.userId(), "AUTH_REFRESH_SUCCEEDED",
                null, null, null, Map.of());

        return new RefreshResult(newAccessToken, newRawRefreshToken, token.userId());
    }
}
