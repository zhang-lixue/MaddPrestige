package net.maddkraft.maddprestige.api.service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.OperationId;

/**
 * Immutable terminal view of one requested progression operation.
 *
 * @param requestId correlation identity allocated before PRE delivery; never a journal key
 * @param durableOperationId journal identity when the operation crossed the PRE durability boundary
 * @param kind requested mutation
 * @param status terminal machine-readable outcome
 * @param error bounded operational error when the terminal outcome needs an explanation
 */
public record OperationResult(
        UUID requestId,
        Optional<OperationId> durableOperationId,
        OperationKind kind,
        OperationStatus status,
        Optional<ServiceError> error) {
    public OperationResult {
        requestId = Objects.requireNonNull(requestId, "request ID");
        durableOperationId = Objects.requireNonNull(durableOperationId, "durable operation ID");
        kind = Objects.requireNonNull(kind, "operation kind");
        status = Objects.requireNonNull(status, "operation status");
        error = Objects.requireNonNull(error, "error");
        if (status == OperationStatus.COMPLETED && error.isPresent()) {
            throw new IllegalArgumentException("A completed operation cannot contain an error");
        }
        if ((status == OperationStatus.COMPLETED || status == OperationStatus.NEEDS_RECONCILIATION)
                && durableOperationId.isEmpty()) {
            throw new IllegalArgumentException("A durable terminal operation requires its journal identity");
        }
        if (durableOperationId.isPresent()
                && durableOperationId.orElseThrow().value().equals(requestId)) {
            throw new IllegalArgumentException("Request correlation and durable operation identities must differ");
        }
    }
}
