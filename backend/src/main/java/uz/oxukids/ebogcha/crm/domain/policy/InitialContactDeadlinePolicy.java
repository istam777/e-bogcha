package uz.oxukids.ebogcha.crm.domain.policy;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class InitialContactDeadlinePolicy {

    public static final Duration INITIAL_CONTACT_WINDOW = Duration.ofHours(24);

    public Instant deadlineFor(Instant creationTime) {
        return Objects.requireNonNull(creationTime, "creationTime must not be null")
                .plus(INITIAL_CONTACT_WINDOW);
    }
}
