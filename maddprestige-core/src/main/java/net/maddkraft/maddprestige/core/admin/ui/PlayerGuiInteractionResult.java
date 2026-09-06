package net.maddkraft.maddprestige.core.admin.ui;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.core.admin.presentation.MessageReference;

/** Result of one consumed player GUI action. */
public record PlayerGuiInteractionResult(
        Optional<GuiSessionView> nextView,
        boolean close,
        List<MessageReference> messages) {
    public PlayerGuiInteractionResult {
        nextView = Objects.requireNonNull(nextView, "next view");
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        if (close && nextView.isPresent()) {
            throw new IllegalArgumentException("A GUI result cannot both close and navigate");
        }
    }

    public static PlayerGuiInteractionResult navigate(GuiSessionView view) {
        return new PlayerGuiInteractionResult(Optional.of(view), false, List.of());
    }

    public static PlayerGuiInteractionResult navigate(
            GuiSessionView view,
            MessageReference message) {
        return new PlayerGuiInteractionResult(Optional.of(view), false, List.of(message));
    }

    public static PlayerGuiInteractionResult closed() {
        return new PlayerGuiInteractionResult(Optional.empty(), true, List.of());
    }

    public static PlayerGuiInteractionResult closed(MessageReference message) {
        return new PlayerGuiInteractionResult(Optional.empty(), true, List.of(message));
    }

    /** Keeps the current authoritative view open while presenting one concise recoverable input finding. */
    public static PlayerGuiInteractionResult stay(MessageReference message) {
        return new PlayerGuiInteractionResult(Optional.empty(), false, List.of(message));
    }
}
