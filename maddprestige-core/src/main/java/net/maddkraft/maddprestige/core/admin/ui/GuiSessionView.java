package net.maddkraft.maddprestige.core.admin.ui;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record GuiSessionView(
        UUID sessionId,
        GuiAudience audience,
        String title,
        List<GuiAction> actions,
        Instant expiresAt) {
    public GuiSessionView {
        sessionId = Objects.requireNonNull(sessionId, "session ID");
        audience = Objects.requireNonNull(audience, "audience");
        title = Objects.requireNonNull(title, "title");
        actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
        expiresAt = Objects.requireNonNull(expiresAt, "expiry");
    }
}
