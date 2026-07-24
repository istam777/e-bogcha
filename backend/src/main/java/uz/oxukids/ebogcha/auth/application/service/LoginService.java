package uz.oxukids.ebogcha.auth.application.service;

public interface LoginService {
    LoginResult login(String login, String password);

    record LoginResult(String accessToken, String refreshToken, java.util.UUID userId) {}
}
