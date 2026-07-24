package uz.oxukids.ebogcha.auth.infrastructure.configuration;

import java.time.Instant;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import uz.oxukids.ebogcha.auth.application.port.out.AuditLogRepository;
import uz.oxukids.ebogcha.auth.application.port.out.AuthUserRepository;
import uz.oxukids.ebogcha.auth.application.port.out.Clock;
import uz.oxukids.ebogcha.auth.application.port.out.PrincipalRepository;
import uz.oxukids.ebogcha.auth.application.port.out.RefreshTokenRepository;
import uz.oxukids.ebogcha.auth.infrastructure.security.JwtTokenProvider;
import uz.oxukids.ebogcha.auth.infrastructure.security.RefreshTokenGenerator;
import uz.oxukids.ebogcha.auth.infrastructure.service.GetCurrentUserServiceImpl;
import uz.oxukids.ebogcha.auth.infrastructure.service.LoginServiceImpl;
import uz.oxukids.ebogcha.auth.infrastructure.service.LogoutServiceImpl;
import uz.oxukids.ebogcha.auth.infrastructure.service.RefreshTokenServiceImpl;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfiguration {

    @Bean
    Clock authClock() {
        return Instant::now;
    }

    @Bean
    BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    LoginServiceImpl loginService(
            AuthUserRepository authUserRepository,
            PrincipalRepository principalRepository,
            RefreshTokenRepository refreshTokenRepository,
            AuditLogRepository auditLogRepository,
            AuthProperties authProperties
    ) {
        var passwordEncoder = new uz.oxukids.ebogcha.auth.infrastructure.security.PasswordEncoder();
        var jwtTokenProvider = new JwtTokenProvider(authProperties);
        var refreshTokenGenerator = new RefreshTokenGenerator();

        return new LoginServiceImpl(
                authUserRepository,
                principalRepository,
                passwordEncoder,
                jwtTokenProvider,
                refreshTokenGenerator,
                refreshTokenRepository,
                auditLogRepository,
                authProperties
        );
    }

    @Bean
    RefreshTokenServiceImpl refreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            PrincipalRepository principalRepository,
            AuditLogRepository auditLogRepository,
            AuthProperties authProperties
    ) {
        var jwtTokenProvider = new JwtTokenProvider(authProperties);
        var refreshTokenGenerator = new RefreshTokenGenerator();

        return new RefreshTokenServiceImpl(
                refreshTokenRepository,
                principalRepository,
                jwtTokenProvider,
                refreshTokenGenerator,
                auditLogRepository
        );
    }

    @Bean
    LogoutServiceImpl logoutService(
            RefreshTokenRepository refreshTokenRepository,
            AuditLogRepository auditLogRepository
    ) {
        var refreshTokenGenerator = new RefreshTokenGenerator();
        return new LogoutServiceImpl(refreshTokenRepository, refreshTokenGenerator, auditLogRepository);
    }

    @Bean
    GetCurrentUserServiceImpl getCurrentUserService(PrincipalRepository principalRepository) {
        return new GetCurrentUserServiceImpl(principalRepository);
    }
}
