package uz.oxukids.ebogcha.auth.application.service;

import java.util.UUID;

public interface LoginService {

    LoginResult login(String login, String password);

    record LoginResult(
            String accessToken,
            String refreshToken,
            UUID userId
    ) {
    }
}
