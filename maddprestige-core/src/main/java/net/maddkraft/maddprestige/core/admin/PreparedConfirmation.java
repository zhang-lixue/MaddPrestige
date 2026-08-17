package net.maddkraft.maddprestige.core.admin;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PreparedConfirmation(UUID confirmationId, OperationPreview preview, Instant expiresAt) {
    public PreparedConfirmation {
        confirmationId = Objects.requireNonNull(confirmationId, "confirmation ID");
        preview = Objects.requireNonNull(preview, "preview");
        expiresAt = Objects.requireNonNull(expiresAt, "expiry");
    }
}
