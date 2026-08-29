package net.maddkraft.maddprestige.api.service;

import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.StageId;

/**
 * Immutable stage-era catalog entry retained for stable source and binary compatibility. Active numeric Prestige has
 * no stage catalog, and production does not expose or execute these entries.
 *
 * @param id canonical stage identity
 * @param enabled historical compatibility participation flag
 * @param ordinal non-negative historical catalog position
 * @param requirementTreeId configured machine identity, or empty when the stage has no requirement tree
 */
public record StageView(StageId id, boolean enabled, int ordinal, Optional<String> requirementTreeId) {
    public StageView {
        id = Objects.requireNonNull(id, "stage ID");
        requirementTreeId = Objects.requireNonNull(requirementTreeId, "requirement tree ID");
        if (ordinal < 0 || requirementTreeId.filter(value ->
                !value.matches("[a-z0-9][a-z0-9._-]{0,63}")).isPresent()) {
            throw new IllegalArgumentException("Stage view is outside stable bounds");
        }
    }
}
