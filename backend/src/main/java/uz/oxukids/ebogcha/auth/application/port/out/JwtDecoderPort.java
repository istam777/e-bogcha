package uz.oxukids.ebogcha.auth.application.port.out;

import org.springframework.security.oauth2.jwt.Jwt;

public interface JwtDecoderPort {
    Jwt decode(String token);
}
