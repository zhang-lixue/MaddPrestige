package net.maddkraft.maddprestige.core.requirement;

import java.time.Instant;
import java.util.Objects;

public record RequirementLatch(LatchKey key, Instant completedAt) {
    public RequirementLatch {
        key = Objects.requireNonNull(key, "key");
        completedAt = Objects.requireNonNull(completedAt, "completed at");
    }
}
