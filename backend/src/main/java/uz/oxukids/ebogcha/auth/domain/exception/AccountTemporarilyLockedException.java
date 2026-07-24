package uz.oxukids.ebogcha.auth.domain.exception;

import java.time.Instant;
import java.util.Objects;

public final class AccountTemporarilyLockedException extends AuthenticationException {

    private final Instant lockedUntil;

    public AccountTemporarilyLockedException(Instant lockedUntil) {
        super("Account temporarily locked until " + lockedUntil);
        this.lockedUntil = Objects.requireNonNull(lockedUntil, "lockedUntil must not be null");
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }
}
