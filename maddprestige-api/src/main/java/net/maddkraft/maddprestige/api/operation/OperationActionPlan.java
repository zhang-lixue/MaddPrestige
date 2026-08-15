package net.maddkraft.maddprestige.api.operation;

import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ProviderId;

public record OperationActionPlan(
        String actionId,
        ProviderId providerId,
        String actionType,
        String redactedDescription,
        boolean reversible,
        boolean idempotent) {
    public OperationActionPlan {
        actionId = Objects.requireNonNull(actionId, "action ID");
        providerId = Objects.requireNonNull(providerId, "provider ID");
        actionType = Objects.requireNonNull(actionType, "action type");
        redactedDescription = Objects.requireNonNull(redactedDescription, "description");
    }
}
