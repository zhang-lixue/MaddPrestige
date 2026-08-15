package net.maddkraft.maddprestige.api.operation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.ProviderId;

public record OperationPlan(
        OperationId id,
        String operationType,
        Actor actor,
        UUID target,
        long expectedStateRevision,
        ConfigRevisionId configRevision,
        Map<ProviderId, Long> providerGenerations,
        String idempotencyKey,
        List<OperationActionPlan> actions,
        String redactedPreview) {
    public OperationPlan {
        id = Objects.requireNonNull(id, "operation ID");
        operationType = Objects.requireNonNull(operationType, "operation type");
        actor = Objects.requireNonNull(actor, "actor");
        target = Objects.requireNonNull(target, "target");
        if (expectedStateRevision < 0) {
            throw new IllegalArgumentException("Expected state revision cannot be negative");
        }
        configRevision = Objects.requireNonNull(configRevision, "configuration revision");
        providerGenerations = Map.copyOf(Objects.requireNonNull(providerGenerations, "provider generations"));
        idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotency key");
        actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
        redactedPreview = Objects.requireNonNull(redactedPreview, "preview");
        if (actions.stream().map(OperationActionPlan::actionId).distinct().count() != actions.size()) {
            throw new IllegalArgumentException("Operation action IDs must be unique");
        }
    }
}
