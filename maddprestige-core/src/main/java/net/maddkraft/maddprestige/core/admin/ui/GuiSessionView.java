package net.maddkraft.maddprestige.core.admin.ui;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.core.admin.presentation.MessageReference;

public record GuiSessionView(
        UUID sessionId,
        GuiAudience audience,
        MessageReference title,
        List<GuiAction> actions,
        Instant expiresAt,
        GuiScreenKind screen,
        int inventorySize,
        List<GuiDisplayItem> items,
        java.util.Optional<GuiTextInput> textInput) {
    public GuiSessionView {
        sessionId = Objects.requireNonNull(sessionId, "session ID");
        audience = Objects.requireNonNull(audience, "audience");
        title = Objects.requireNonNull(title, "title");
        actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
        expiresAt = Objects.requireNonNull(expiresAt, "expiry");
        screen = Objects.requireNonNull(screen, "screen");
        if (inventorySize < 9 || inventorySize > 54 || inventorySize % 9 != 0) {
            throw new IllegalArgumentException(
                    "GUI inventory size must be a multiple of nine from nine to fifty-four");
        }
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        textInput = Objects.requireNonNull(textInput, "text input");
        if (textInput.isPresent() && inventorySize != 9) {
            throw new IllegalArgumentException("GUI text input uses the three-slot platform view backed by size nine");
        }
        java.util.Set<Integer> slots = new java.util.HashSet<>();
        java.util.Set<UUID> actionIds = actions.stream().map(GuiAction::actionId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        for (GuiDisplayItem item : items) {
            if (item.slot() >= inventorySize || !slots.add(item.slot())) {
                throw new IllegalArgumentException("GUI display slots must be unique and inside the inventory");
            }
            if (item.actionId().isPresent() && !actionIds.contains(item.actionId().orElseThrow())) {
                throw new IllegalArgumentException("GUI display action must belong to its server-owned session");
            }
        }
    }

    public GuiSessionView(
            UUID sessionId,
            GuiAudience audience,
            MessageReference title,
            List<GuiAction> actions,
            Instant expiresAt,
            GuiScreenKind screen,
            int inventorySize,
            List<GuiDisplayItem> items) {
        this(sessionId, audience, title, actions, expiresAt, screen, inventorySize, items,
                java.util.Optional.empty());
    }

    public GuiSessionView(
            UUID sessionId,
            GuiAudience audience,
            MessageReference title,
            List<GuiAction> actions,
            Instant expiresAt) {
        this(sessionId, audience, title, actions, expiresAt, GuiScreenKind.LEGACY,
                Math.max(9, Math.min(54, ((actions.size() + 8) / 9) * 9)), List.of(),
                java.util.Optional.empty());
    }
}
