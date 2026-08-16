package net.maddkraft.maddprestige.core.season;

import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.ScopeId;
import net.maddkraft.maddprestige.api.id.SeasonId;

public record ActiveSeasonContext(Optional<SeasonId> seasonId, Optional<ScopeId> scopeId) {
    public ActiveSeasonContext {
        seasonId = Objects.requireNonNull(seasonId, "season ID");
        scopeId = Objects.requireNonNull(scopeId, "scope ID");
        if (seasonId.isPresent() != scopeId.isPresent()) {
            throw new IllegalArgumentException("Active season and scope must be present together");
        }
    }

    public static ActiveSeasonContext none() {
        return new ActiveSeasonContext(Optional.empty(), Optional.empty());
    }
}
