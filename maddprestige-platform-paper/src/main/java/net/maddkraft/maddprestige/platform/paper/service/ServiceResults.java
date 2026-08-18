package net.maddkraft.maddprestige.platform.paper.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.service.OperationKind;
import net.maddkraft.maddprestige.api.service.OperationResult;
import net.maddkraft.maddprestige.api.service.OperationStatus;
import net.maddkraft.maddprestige.api.service.ServiceError;

/** Stable result translation helpers for the Paper composition root. */
public final class ServiceResults {
    private ServiceResults() {
    }

    public static OperationResult conflict(OperationKind kind, String code, String messageKey) {
        return conflict(UUID.randomUUID(), kind, code, messageKey);
    }

    public static OperationResult conflict(UUID requestId, OperationKind kind, String code, String messageKey) {
        return failure(requestId, kind, OperationStatus.CONFLICT, code, messageKey);
    }

    public static OperationResult blocked(OperationKind kind, String code, String messageKey) {
        return blocked(UUID.randomUUID(), kind, code, messageKey);
    }

    public static OperationResult blocked(UUID requestId, OperationKind kind, String code, String messageKey) {
        return failure(requestId, kind, OperationStatus.BLOCKED, code, messageKey);
    }

    public static OperationResult failure(
            OperationKind kind,
            OperationStatus status,
            String code,
            String messageKey) {
        return failure(UUID.randomUUID(), kind, status, code, messageKey);
    }

    public static OperationResult failure(
            UUID requestId,
            OperationKind kind,
            OperationStatus status,
            String code,
            String messageKey) {
        return new OperationResult(requestId, Optional.empty(), kind, status,
                Optional.of(new ServiceError(code, messageKey, Map.of())));
    }
}
