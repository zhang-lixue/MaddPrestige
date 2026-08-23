package net.maddkraft.maddprestige.core.admin.command;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.core.admin.presentation.MessageReference;
import net.maddkraft.maddprestige.core.admin.ui.GuiSessionView;

public record CommandResponse(
        boolean successful,
        String code,
        List<MessageReference> messages,
        Optional<GuiSessionView> guiView) {
    public CommandResponse {
        code = Objects.requireNonNull(code, "code");
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        guiView = Objects.requireNonNull(guiView, "GUI view");
        if (code.isBlank() || messages.isEmpty()) {
            throw new IllegalArgumentException("Command response requires a code and at least one line");
        }
    }

    public static CommandResponse success(String code, List<MessageReference> messages) {
        return new CommandResponse(true, code, messages, Optional.empty());
    }

    public static CommandResponse gui(String code, List<MessageReference> messages, GuiSessionView view) {
        return new CommandResponse(true, code, messages, Optional.of(view));
    }

    public static CommandResponse failure(String code, MessageReference message) {
        return new CommandResponse(false, code, List.of(message), Optional.empty());
    }

    public static CommandResponse failure(String code, List<MessageReference> messages) {
        return new CommandResponse(false, code, messages, Optional.empty());
    }

    /** Machine-only compatibility view for diagnostics and older platform-neutral tests. */
    public List<String> lines() {
        return messages.stream().map(MessageReference::diagnosticForm).toList();
    }
}
