package net.maddkraft.maddprestige.core.prestige;

import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.ScopeId;
import net.maddkraft.maddprestige.api.id.SeasonId;

public record SeasonConsequence(
        Optional<SeasonId> seasonId,
        Optional<ScopeId> scopeId,
        String effect) {
    public SeasonConsequence {
        seasonId = Objects.requireNonNull(seasonId, "season ID");
        scopeId = Objects.requireNonNull(scopeId, "scope ID");
        effect = Objects.requireNonNull(effect, "effect");
    }
}
