package uz.oxukids.ebogcha.auth.application.service;

public interface LogoutService {
    void logout(String refreshToken, String ip, String userAgent);
}
