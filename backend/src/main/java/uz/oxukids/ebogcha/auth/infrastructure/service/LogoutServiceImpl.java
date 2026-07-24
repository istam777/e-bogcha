package uz.oxukids.ebogcha.auth.infrastructure.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.oxukids.ebogcha.auth.application.port.out.AuditLogRepository;
import uz.oxukids.ebogcha.auth.application.port.out.Clock;
import uz.oxukids.ebogcha.auth.application.port.out.RefreshTokenRepository;
import uz.oxukids.ebogcha.auth.application.service.LogoutService;
import uz.oxukids.ebogcha.auth.infrastructure.security.AuthRefreshTokenGenerator;

import java.util.Map;

@Service
class LogoutServiceImpl implements LogoutService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthRefreshTokenGenerator refreshTokenGenerator;
    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    LogoutServiceImpl(
            RefreshTokenRepository refreshTokenRepository,
            AuthRefreshTokenGenerator refreshTokenGenerator,
            AuditLogRepository auditLogRepository,
            Clock clock
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void logout(String rawRefreshToken, String ip, String userAgent) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) return;

        String tokenHash = refreshTokenGenerator.hashToken(rawRefreshToken);
        var tokenOpt = refreshTokenRepository.findByTokenHashForUpdate(tokenHash);

        if (tokenOpt.isPresent() && !tokenOpt.get().isRevoked()) {
            var token = tokenOpt.get();
            refreshTokenRepository.revokeById(token.id(), clock.now());
            auditLogRepository.write(null, token.userId(), token.userId(),
                    "USER", token.userId(), "AUTH_LOGOUT",
                    null, ip, userAgent, Map.of());
        }
    }
}
