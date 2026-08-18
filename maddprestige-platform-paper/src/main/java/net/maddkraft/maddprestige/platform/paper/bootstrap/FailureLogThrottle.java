package net.maddkraft.maddprestige.platform.paper.bootstrap;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Thread-safe repeated-failure log throttle; semantic health updates remain unthrottled. */
final class FailureLogThrottle {
    private final Clock clock;
    private final Duration interval;
    private final AtomicReference<Instant> nextLog = new AtomicReference<>(Instant.MIN);

    FailureLogThrottle(Clock clock, Duration interval) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.interval = Objects.requireNonNull(interval, "interval");
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("Failure log interval must be positive");
        }
    }

    boolean acquire() {
        Instant now = clock.instant();
        while (true) {
            Instant current = nextLog.get();
            if (now.isBefore(current)) {
                return false;
            }
            if (nextLog.compareAndSet(current, now.plus(interval))) {
                return true;
            }
        }
    }

    void recovered() {
        nextLog.set(Instant.MIN);
    }
}
