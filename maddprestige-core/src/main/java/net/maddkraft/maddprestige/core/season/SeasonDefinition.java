package net.maddkraft.maddprestige.core.season;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.id.SeasonId;
import net.maddkraft.maddprestige.core.config.lifecycle.ResetDisposition;

public record SeasonDefinition(
        SeasonId id,
        String displayName,
        Optional<Instant> startsAt,
        Optional<Instant> endsAt,
        ResetDisposition progressPolicy,
        Map<RequirementId, RequirementId> requirementOverrides,
        Map<RequirementId, String> catchUpProfileReferences) {
    public SeasonDefinition {
        id = Objects.requireNonNull(id, "season ID");
        displayName = Objects.requireNonNull(displayName, "display name");
        startsAt = Objects.requireNonNull(startsAt, "start time");
        endsAt = Objects.requireNonNull(endsAt, "end time");
        progressPolicy = Objects.requireNonNull(progressPolicy, "season progress policy");
        requirementOverrides = Map.copyOf(Objects.requireNonNull(requirementOverrides, "requirement overrides"));
        catchUpProfileReferences = Map.copyOf(Objects.requireNonNull(
                catchUpProfileReferences, "catch-up profile references"));
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("Season display name cannot be blank");
        }
        if (startsAt.isPresent() && endsAt.isPresent()
                && !startsAt.orElseThrow().isBefore(endsAt.orElseThrow())) {
            throw new IllegalArgumentException("Season start must precede end");
        }
    }
}
