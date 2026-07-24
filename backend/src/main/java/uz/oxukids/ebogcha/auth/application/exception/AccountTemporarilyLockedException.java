package uz.oxukids.ebogcha.auth.application.exception;

public final class AccountTemporarilyLockedException extends RuntimeException {

    private final int retryAfterSeconds;

    public AccountTemporarilyLockedException(int retryAfterSeconds) {
        super("Account temporarily locked due to too many failed attempts");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
