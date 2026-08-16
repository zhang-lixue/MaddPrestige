package net.maddkraft.maddprestige.core.competition;

import java.util.Objects;
import java.util.Optional;

/** Explicit default-off boundary; the larger generic competition engine remains deferred. */
public final class CompetitionFeatureBoundary {
    private final Optional<Object> activeService;

    public CompetitionFeatureBoundary(CompetitionConfiguration configuration) {
        Objects.requireNonNull(configuration, "competition configuration");
        if (configuration.enabled()) {
            throw new IllegalArgumentException("Competition engine is not implemented; enabled configuration is invalid");
        }
        activeService = Optional.empty();
    }

    public Optional<Object> activeService() {
        return activeService;
    }

    public boolean contributesCommands() {
        return false;
    }

    public boolean contributesUserInterface() {
        return false;
    }
}
