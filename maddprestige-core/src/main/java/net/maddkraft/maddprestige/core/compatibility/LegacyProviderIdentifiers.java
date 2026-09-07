package net.maddkraft.maddprestige.core.compatibility;

import java.util.Map;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ProviderId;

/** Exact aliases retained for configuration and persistence compatibility. */
public final class LegacyProviderIdentifiers {
    public static final ProviderId EVENT_PROGRESS = new ProviderId("event_progress");
    private static final ProviderId HISTORICAL_EVENT_PROGRESS = new ProviderId("phase5_events");
    private static final Map<ProviderId, ProviderId> CANONICAL_BY_LEGACY = Map.of(
            HISTORICAL_EVENT_PROGRESS, EVENT_PROGRESS);
    private static final Map<ProviderId, ProviderId> STORAGE_BY_CANONICAL = Map.of(
            EVENT_PROGRESS, HISTORICAL_EVENT_PROGRESS);

    private LegacyProviderIdentifiers() {
    }

    public static ProviderId canonicalize(ProviderId providerId) {
        ProviderId value = Objects.requireNonNull(providerId, "provider ID");
        return CANONICAL_BY_LEGACY.getOrDefault(value, value);
    }

    public static ProviderId historicalStorageKey(ProviderId providerId) {
        ProviderId canonical = canonicalize(providerId);
        return STORAGE_BY_CANONICAL.getOrDefault(canonical, canonical);
    }

    public static boolean isLegacy(ProviderId providerId) {
        return CANONICAL_BY_LEGACY.containsKey(Objects.requireNonNull(providerId, "provider ID"));
    }
}
