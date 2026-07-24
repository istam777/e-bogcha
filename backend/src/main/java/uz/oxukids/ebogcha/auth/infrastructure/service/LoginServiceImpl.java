package uz.oxukids.ebogcha.auth.infrastructure.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.oxukids.ebogcha.auth.application.exception.AccountDisabledException;
import uz.oxukids.ebogcha.auth.application.exception.AccountTemporarilyLockedException;
import uz.oxukids.ebogcha.auth.application.exception.InvalidCredentialsException;
import uz.oxukids.ebogcha.auth.application.port.out.AuthUserRecord;
import uz.oxukids.ebogcha.auth.application.port.out.AuthUserRepository;
import uz.oxukids.ebogcha.auth.application.port.out.AuditLogRepository;
import uz.oxukids.ebogcha.auth.application.port.out.PrincipalRepository;
import uz.oxukids.ebogcha.auth.application.port.out.RefreshTokenRepository;
import uz.oxukids.ebogcha.auth.application.service.LoginService;
import uz.oxukids.ebogcha.auth.domain.principal.AuthenticatedPrincipal;
import uz.oxukids.ebogcha.auth.infrastructure.configuration.AuthProperties;
import uz.oxukids.ebogcha.auth.infrastructure.security.JwtTokenProvider;
import uz.oxukids.ebogcha.auth.infrastructure.security.PasswordEncoder;
import uz.oxukids.ebogcha.auth.infrastructure.security.RefreshTokenGenerator;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class LoginServiceImpl implements LoginService {

    private final AuthUserRepository authUserRepository;
    private final PrincipalRepository principalRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuthProperties authProperties;

    public LoginServiceImpl(
            AuthUserRepository authUserRepository,
            PrincipalRepository principalRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenGenerator refreshTokenGenerator,
            RefreshTokenRepository refreshTokenRepository,
            AuditLogRepository auditLogRepository,
            AuthProperties authProperties
    ) {
        this.authUserRepository = authUserRepository;
        this.principalRepository = principalRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditLogRepository = auditLogRepository;
        this.authProperties = authProperties;
    }

    @Override
    @Transactional
    public LoginResult login(String login, String password) {
        Instant now = Instant.now();
        String normalizedLogin = login.strip().toLowerCase(java.util.Locale.ROOT);

        // Dummy verification for unknown users to prevent timing attacks
        int matchCount = authUserRepository.countActiveByUsernameAcrossOrganizations(normalizedLogin);
        if (matchCount != 1) {
            passwordEncoder.matches(password, "$2a$12$dummyhashdummyhashdummyhashdummyhashdummyhashdu");
            auditLogRepository.write(null, null, null, "USER", UUID.randomUUID(),
                    "AUTH_LOGIN_FAILED", null, null, null, Map.of("reason", "INVALID_CREDENTIALS"));
            throw new InvalidCredentialsException();
        }

        // Find user
        var userOpt = authUserRepository.findByNormalizedUsername(normalizedLogin);
        if (userOpt.isEmpty()) {
            passwordEncoder.matches(password, "$2a$12$dummyhashdummyhashdummyhashdummyhashdummyhashdu");
            auditLogRepository.write(null, null, null, "USER", UUID.randomUUID(),
                    "AUTH_LOGIN_FAILED", null, null, null, Map.of("reason", "INVALID_CREDENTIALS"));
            throw new InvalidCredentialsException();
        }

        AuthUserRecord user = userOpt.get();

        // Check eligibility BEFORE checking password
        if (!user.isAuthenticationEligible()) {
            // Still verify password to prevent timing leak
            var passwordHash = authUserRepository.findPasswordHashByUserId(user.id());
            passwordHash.ifPresent(hash -> passwordEncoder.matches(password, hash));
            auditLogRepository.write(user.organizationId(), user.id(), user.id(), "USER", user.id(),
                    "AUTH_LOGIN_FAILED", null, null, null, Map.of("reason", "ACCOUNT_DISABLED"));
            throw new AccountDisabledException();
        }

        // Find credentials
        var passwordHash = authUserRepository.findPasswordHashByUserId(user.id());
        if (passwordHash.isEmpty()) {
            passwordEncoder.matches(password, "$2a$12$dummyhashdummyhashdummyhashdummyhashdummyhashdu");
            auditLogRepository.write(user.organizationId(), user.id(), user.id(), "USER", user.id(),
                    "AUTH_LOGIN_FAILED", null, null, null, Map.of("reason", "INVALID_CREDENTIALS"));
            throw new InvalidCredentialsException();
        }

        // Check lockout
        var lockedUntil = authUserRepository.findLockedUntilByUserId(user.id());
        if (lockedUntil.isPresent() && now.isBefore(lockedUntil.get())) {
            passwordEncoder.matches(password, passwordHash.get());
            int retryAfter = (int) Duration.between(now, lockedUntil.get()).getSeconds();
            auditLogRepository.write(user.organizationId(), user.id(), user.id(), "USER", user.id(),
                    "AUTH_LOGIN_FAILED", null, null, null, Map.of("reason", "ACCOUNT_TEMPORARILY_LOCKED"));
            throw new AccountTemporarilyLockedException(retryAfter);
        }

        // Verify password
        if (!passwordEncoder.matches(password, passwordHash.get())) {
            int newCount = authUserRepository.findFailedLoginAttempts(user.id()) + 1;
            Instant newLockedUntil = null;
            if (newCount >= authProperties.lockout().maxFailedAttempts()) {
                newLockedUntil = now.plus(authProperties.lockout().lockDuration());
                auditLogRepository.write(user.organizationId(), user.id(), user.id(), "USER", user.id(),
                        "AUTH_ACCOUNT_TEMPORARILY_LOCKED", null, null, null,
                        Map.of("failedAttempts", newCount));
            }
            authUserRepository.incrementFailedLoginAttempts(user.id(), newCount, newLockedUntil);
            auditLogRepository.write(user.organizationId(), user.id(), user.id(), "USER", user.id(),
                    "AUTH_LOGIN_FAILED", null, null, null, Map.of("failedAttempts", newCount));
            throw new InvalidCredentialsException();
        }

        // Success - reset counters
        authUserRepository.resetFailedLoginAttempts(user.id(), now);

        // Load principal and create tokens
        AuthenticatedPrincipal principal = principalRepository.loadPrincipal(user.id());
        String accessToken = jwtTokenProvider.createAccessToken(principal);

        String rawRefreshToken = refreshTokenGenerator.generateRawToken();
        String refreshHash = refreshTokenGenerator.hashToken(rawRefreshToken);
        UUID refreshId = UUID.randomUUID();
        refreshTokenRepository.save(refreshId, user.id(), refreshHash,
                now.plus(Duration.ofDays(30)), null, null);

        auditLogRepository.write(user.organizationId(), user.id(), user.id(), "USER", user.id(),
                "AUTH_LOGIN_SUCCEEDED", null, null, null, Map.of());

        return new LoginResult(accessToken, rawRefreshToken, user.id());
    }
}
