package net.maddkraft.maddprestige.api.audit;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.operation.Actor;

public record AuditRecord(
        UUID auditId,
        Actor actor,
        Optional<UUID> target,
        Optional<OperationId> operationId,
        Optional<ConfigRevisionId> configRevision,
        String providerAction,
        Optional<AuditValue> oldValue,
        Optional<AuditValue> newValue,
        String sourceSurface,
        String reason,
        AuditOutcome outcome,
        Optional<String> failureOrUncertainty,
        UUID correlationId,
        Instant timestamp) {
    public AuditRecord {
        auditId = Objects.requireNonNull(auditId, "audit ID");
        actor = Objects.requireNonNull(actor, "actor");
        target = Objects.requireNonNull(target, "target");
        operationId = Objects.requireNonNull(operationId, "operation ID");
        configRevision = Objects.requireNonNull(configRevision, "configuration revision");
        providerAction = Objects.requireNonNull(providerAction, "provider action");
        oldValue = Objects.requireNonNull(oldValue, "old value");
        newValue = Objects.requireNonNull(newValue, "new value");
        sourceSurface = Objects.requireNonNull(sourceSurface, "source surface");
        reason = Objects.requireNonNull(reason, "reason");
        outcome = Objects.requireNonNull(outcome, "outcome");
        failureOrUncertainty = Objects.requireNonNull(failureOrUncertainty, "failure or uncertainty");
        correlationId = Objects.requireNonNull(correlationId, "correlation ID");
        timestamp = Objects.requireNonNull(timestamp, "timestamp");
    }
}
