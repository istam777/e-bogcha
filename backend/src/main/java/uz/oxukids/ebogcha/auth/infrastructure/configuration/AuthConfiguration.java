package uz.oxukids.ebogcha.auth.infrastructure.configuration;

import java.time.Instant;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import uz.oxukids.ebogcha.auth.application.port.out.Clock;
import uz.oxukids.ebogcha.auth.infrastructure.security.AuthRefreshTokenGenerator;
import uz.oxukids.ebogcha.auth.infrastructure.security.CurrentPrincipalResolver;

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
    SecretKey jwtSecretKey(@Value("${auth.jwt.secret-base64}") String secretBase64) {
        byte[] keyBytes = java.util.Base64.getDecoder().decode(secretBase64);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret must decode to at least 32 bytes, got " + keyBytes.length);
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(
            SecretKey jwtSecretKey,
            @Value("${auth.jwt.issuer:e-bogcha}") String issuer
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey).build();
        decoder.setJwtValidator(
            org.springframework.security.oauth2.jwt.JwtValidators.createDefaultWithIssuer(issuer)
        );
        return decoder;
    }

    @Bean
    AuthRefreshTokenGenerator authRefreshTokenGenerator() {
        return new AuthRefreshTokenGenerator();
    }

    @Bean
    CurrentPrincipalResolver currentPrincipalResolver() {
        return new CurrentPrincipalResolver();
    }
}
