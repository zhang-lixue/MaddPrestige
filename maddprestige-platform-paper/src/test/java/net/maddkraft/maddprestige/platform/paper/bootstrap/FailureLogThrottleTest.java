package net.maddkraft.maddprestige.platform.paper.bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FailureLogThrottleTest {
    @Test
    @DisplayName("[OR8B-08] Repeated flush failures are rate-limited and recovery resets the episode")
    void rateLimitsOneFailureEpisodeAndResetsAfterRecovery() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-17T20:00:00Z"), ZoneOffset.UTC);
        FailureLogThrottle throttle = new FailureLogThrottle(clock, Duration.ofMinutes(5));

        assertTrue(throttle.acquire());
        assertFalse(throttle.acquire());
        clock.advance(Duration.ofMinutes(4));
        assertFalse(throttle.acquire());
        clock.advance(Duration.ofMinutes(1));
        assertTrue(throttle.acquire());
        throttle.recovered();
        assertTrue(throttle.acquire());
    }

    private static final class MutableClock extends Clock {
        private Instant current;
        private final ZoneId zone;

        private MutableClock(Instant current, ZoneId zone) {
            this.current = current;
            this.zone = zone;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId replacement) {
            return new MutableClock(current, replacement);
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
