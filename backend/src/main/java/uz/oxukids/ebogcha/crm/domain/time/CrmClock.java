package uz.oxukids.ebogcha.crm.domain.time;

import java.time.Instant;

@FunctionalInterface
public interface CrmClock {

    Instant now();
}
