package uz.oxukids.ebogcha.auth.application.service;

import java.util.UUID;

public interface RefreshTokenService {

    RefreshResult refresh(String refreshToken);

    record RefreshResult(
            String accessToken,
            String refreshToken,
            UUID userId
    ) {
    }
}
