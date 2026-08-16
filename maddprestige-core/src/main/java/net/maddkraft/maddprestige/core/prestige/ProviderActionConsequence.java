package net.maddkraft.maddprestige.core.prestige;

import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ProviderId;

public record ProviderActionConsequence(
        String actionId,
        ProviderId providerId,
        String description,
        boolean external,
        boolean idempotent,
        boolean uncertaintyPossible) {
    public ProviderActionConsequence {
        actionId = Objects.requireNonNull(actionId, "action ID");
        providerId = Objects.requireNonNull(providerId, "provider ID");
        description = Objects.requireNonNull(description, "description");
    }
}
