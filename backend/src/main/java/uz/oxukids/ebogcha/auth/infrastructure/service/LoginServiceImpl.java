package uz.oxukids.ebogcha.auth.infrastructure.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.oxukids.ebogcha.auth.application.port.out.AuthUserRepository;
import uz.oxukids.ebogcha.auth.application.port.out.AuthUserRepository.AuthUserRecord;
import uz.oxukids.ebogcha.auth.application.port.out.AuthUserRepository.CredentialRecord;
import uz.oxukids.ebogcha.auth.application.port.out.AuditLogRepository;
import uz.oxukids.ebogcha.auth.application.port.out.Clock;
import uz.oxukids.ebogcha.auth.application.port.out.PrincipalRepository;
import uz.oxukids.ebogcha.auth.application.port.out.RefreshTokenRepository;
import uz.oxukids.ebogcha.auth.application.service.LoginService;
import uz.oxukids.ebogcha.auth.domain.exception.AccountDisabledException;
import uz.oxukids.ebogcha.auth.domain.exception.AccountTemporarilyLockedException;
import uz.oxukids.ebogcha.auth.domain.exception.InvalidCredentialsException;
import uz.oxukids.ebogcha.auth.domain.principal.AuthenticatedPrincipal;
import uz.oxukids.ebogcha.auth.infrastructure.configuration.AuthProperties;
import uz.oxukids.ebogcha.auth.infrastructure.security.AuthJwtEncoder;
import uz.oxukids.ebogcha.auth.infrastructure.security.AuthRefreshTokenGenerator;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
class LoginServiceImpl implements LoginService {

    private static final String DUMMY_BCRYPT = "$2a$12$LJ3m4ys3Lz0YBNOURq0Y4OjMKEl8Ht4lMfqd7Mf3kN5Vz8Gx2S1aG";

    private final AuthUserRepository authUserRepository;
    private final PrincipalRepository principalRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final AuthJwtEncoder jwtEncoder;
    private final AuthRefreshTokenGenerator refreshTokenGenerator;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuthProperties authProperties;
    private final Clock clock;

    LoginServiceImpl(
            AuthUserRepository authUserRepository,
            PrincipalRepository principalRepository,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
            AuthJwtEncoder jwtEncoder,
            AuthRefreshTokenGenerator refreshTokenGenerator,
            RefreshTokenRepository refreshTokenRepository,
            AuditLogRepository auditLogRepository,
            AuthProperties authProperties,
            Clock clock
    ) {
        this.authUserRepository = authUserRepository;
        this.principalRepository = principalRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditLogRepository = auditLogRepository;
        this.authProperties = authProperties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public LoginResult login(String login, String password) {
        Instant now = clock.now();
        String normalizedLogin = login.strip().toLowerCase(java.util.Locale.ROOT);

        // Step 1: Resolve candidates without status filtering
        int candidateCount = authUserRepository.countByUsernameAcrossOrganizations(normalizedLogin);

        if (candidateCount != 1) {
            passwordEncoder.matches(password, DUMMY_BCRYPT);
            return rejectCredentials(now, null, null);
        }

        // Step 2: Load unique candidate
        var userOpt = authUserRepository.findByNormalizedUsername(normalizedLogin);
        if (userOpt.isEmpty()) {
            passwordEncoder.matches(password, DUMMY_BCRYPT);
            return rejectCredentials(now, null, null);
        }

        AuthUserRecord user = userOpt.get();

        // Step 3: Load credentials with FOR UPDATE
        var credentialsOpt = authUserRepository.findCredentialsByUserIdForUpdate(user.id());
        if (credentialsOpt.isEmpty()) {
            passwordEncoder.matches(password, DUMMY_BCRYPT);
            return rejectCredentials(now, user.organizationId(), user.id());
        }

        CredentialRecord credentials = credentialsOpt.get();

        // Step 4: Check temporary lock (after password verification attempt)
        if (credentials.isLocked(now)) {
            passwordEncoder.matches(password, credentials.passwordHash());
            throw new AccountTemporarilyLockedException(credentials.lockedUntil());
        }

        // Step 5: Verify password BEFORE checking account status
        if (!passwordEncoder.matches(password, credentials.passwordHash())) {
            handleFailedLogin(user, credentials, now);
            throw new InvalidCredentialsException();
        }

        // Step 6: Only after correct password — check account status
        if (!user.isAuthenticationEligible()) {
            throw new AccountDisabledException("Account disabled");
        }

        // Step 7: Success — reset counters and update last login
        authUserRepository.resetFailedLoginAttempts(user.id(), now);
        authUserRepository.updateLastLoginAt(user.id(), now);

        // Load full principal
        AuthenticatedPrincipal principal = principalRepository.loadPrincipal(user.id());

        // Create access token
        String accessToken = jwtEncoder.encode(principal);

        // Create refresh token
        String rawRefreshToken = refreshTokenGenerator.generateRawToken();
        String refreshHash = refreshTokenGenerator.hashToken(rawRefreshToken);
        UUID refreshId = UUID.randomUUID();
        refreshTokenRepository.save(refreshId, user.id(), refreshHash,
                now.plus(Duration.ofSeconds(authProperties.jwt().refreshExpirationSeconds())), null, null);

        // Audit success
        auditLogRepository.write(user.organizationId(), user.id(), user.id(),
                "USER", user.id(), "AUTH_LOGIN_SUCCEEDED", null, null, null, Map.of());

        return new LoginResult(accessToken, rawRefreshToken, user.id());
    }

    private void handleFailedLogin(AuthUserRecord user, CredentialRecord credentials, Instant now) {
        int newCount = credentials.failedLoginAttempts() + 1;
        Instant newLockedUntil = null;
        if (newCount >= authProperties.lockout().maxFailedAttempts()) {
            newLockedUntil = now.plus(authProperties.lockout().lockDuration());
            auditLogRepository.write(user.organizationId(), user.id(), user.id(),
                    "USER", user.id(), "AUTH_ACCOUNT_TEMPORARILY_LOCKED", null, null, null,
                    Map.of("failedAttempts", newCount));
        }
        authUserRepository.incrementFailedLoginAttempts(user.id(), newCount, newLockedUntil);
        auditLogRepository.write(user.organizationId(), user.id(), user.id(),
                "USER", user.id(), "AUTH_LOGIN_FAILED", null, null, null,
                Map.of("failedAttempts", newCount));
    }

    private LoginResult rejectCredentials(Instant now, UUID orgId, UUID userId) {
        if (userId != null) {
            auditLogRepository.write(orgId, userId, userId,
                    "USER", userId, "AUTH_LOGIN_FAILED", null, null, null,
                    Map.of("reason", "INVALID_CREDENTIALS"));
        }
        throw new InvalidCredentialsException();
    }
}
