package net.maddkraft.maddprestige.integrations.worldguard;

import java.util.UUID;

/** Narrow read-only seam for exact WorldGuard region membership. */
public interface WorldGuardRegionAccess {
    Observation observe(UUID playerId, UUID worldId, String regionId);

    record Observation(Status status, String detail) {
        public Observation {
            java.util.Objects.requireNonNull(status, "status");
            java.util.Objects.requireNonNull(detail, "detail");
        }

        public static Observation inside() {
            return new Observation(Status.INSIDE, "inside");
        }

        public static Observation outside() {
            return new Observation(Status.OUTSIDE, "outside");
        }

        public static Observation unavailable(String detail) {
            return new Observation(Status.UNAVAILABLE, detail);
        }
    }

    enum Status {
        INSIDE,
        OUTSIDE,
        UNAVAILABLE
    }
}
