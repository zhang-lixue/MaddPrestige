package net.maddkraft.maddprestige.testkit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class FailureInjector {
    private final Map<String, InjectedFailure> failures = new ConcurrentHashMap<>();

    public void failNext(String point, int times, RuntimeException failure) {
        if (times < 1) {
            throw new IllegalArgumentException("Failure count must be positive");
        }
        failures.put(point, new InjectedFailure(new AtomicInteger(times), failure));
    }

    public void check(String point) {
        InjectedFailure injected = failures.get(point);
        if (injected == null) {
            return;
        }
        if (injected.remaining.getAndDecrement() > 0) {
            if (injected.remaining.get() == 0) {
                failures.remove(point, injected);
            }
            throw injected.failure;
        }
    }

    private record InjectedFailure(AtomicInteger remaining, RuntimeException failure) {
    }
}
