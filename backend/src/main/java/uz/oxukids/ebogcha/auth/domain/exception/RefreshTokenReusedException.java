package uz.oxukids.ebogcha.auth.domain.exception;

import java.util.Objects;
import java.util.UUID;

public final class RefreshTokenReusedException extends AuthenticationException {

    private final UUID userId;

    public RefreshTokenReusedException(UUID userId) {
        super("Refresh token reuse detected for user " + userId);
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
    }

    public UUID userId() {
        return userId;
    }
}
