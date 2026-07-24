package uz.oxukids.ebogcha.auth.application.port.out;

@FunctionalInterface
public interface Clock {
    java.time.Instant now();
}
