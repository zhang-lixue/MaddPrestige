package net.maddkraft.maddprestige.api.service;

import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.StageId;

/**
 * Immutable in-memory stage catalog entry without rendered display text or mutable implementation state.
 *
 * @param id canonical stage identity
 * @param enabled whether the stage participates in canonical progression
 * @param ordinal non-negative canonical catalog position
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
