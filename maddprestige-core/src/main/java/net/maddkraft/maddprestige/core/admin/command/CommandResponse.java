package net.maddkraft.maddprestige.core.admin.command;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.core.admin.ui.GuiSessionView;

public record CommandResponse(
        boolean successful,
        String code,
        List<String> lines,
        Optional<GuiSessionView> guiView) {
    public CommandResponse {
        code = Objects.requireNonNull(code, "code");
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        guiView = Objects.requireNonNull(guiView, "GUI view");
        if (code.isBlank() || lines.isEmpty()) {
            throw new IllegalArgumentException("Command response requires a code and at least one line");
        }
    }

    public static CommandResponse success(String code, List<String> lines) {
        return new CommandResponse(true, code, lines, Optional.empty());
    }

    public static CommandResponse gui(String code, List<String> lines, GuiSessionView view) {
        return new CommandResponse(true, code, lines, Optional.of(view));
    }

    public static CommandResponse failure(String code, String message, String remediation) {
        return new CommandResponse(false, code, List.of(message, "Next: " + remediation), Optional.empty());
    }
}
