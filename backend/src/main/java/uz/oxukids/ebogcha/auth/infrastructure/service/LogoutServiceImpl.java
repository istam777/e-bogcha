package uz.oxukids.ebogcha.auth.infrastructure.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.oxukids.ebogcha.auth.application.port.out.AuditLogRepository;
import uz.oxukids.ebogcha.auth.application.port.out.RefreshTokenRepository;
import uz.oxukids.ebogcha.auth.application.service.LogoutService;
import uz.oxukids.ebogcha.auth.infrastructure.security.RefreshTokenGenerator;

import java.util.Map;

@Service
public class LogoutServiceImpl implements LogoutService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final AuditLogRepository auditLogRepository;

    public LogoutServiceImpl(
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenGenerator refreshTokenGenerator,
            AuditLogRepository auditLogRepository
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        String tokenHash = refreshTokenGenerator.hashToken(rawRefreshToken);
        var tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);

        if (tokenOpt.isPresent()) {
            var token = tokenOpt.get();
            if (!token.isRevoked()) {
                refreshTokenRepository.revokeById(token.id());
                auditLogRepository.write(null, token.userId(), token.userId(),
                        "USER", token.userId(), "AUTH_LOGOUT",
                        null, null, null, Map.of());
            }
        }
    }
}
