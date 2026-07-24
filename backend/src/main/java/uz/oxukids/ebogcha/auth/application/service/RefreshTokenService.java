package uz.oxukids.ebogcha.auth.application.service;

public interface RefreshTokenService {
    RefreshResult refresh(String refreshToken, String ip, String userAgent);

    record RefreshResult(String accessToken, String refreshToken, java.util.UUID userId) {}
}
