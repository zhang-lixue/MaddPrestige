package net.maddkraft.maddprestige.platform.paper.admin;

import java.util.List;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

/** Player root ingress that opens the canonical GUI without exposing the advanced command namespace. */
public final class PaperPlayerGuiCommandAdapter implements CommandExecutor, TabCompleter {
    private final PaperPhaseSixCommandAdapter delegate;

    public PaperPlayerGuiCommandAdapter(PaperPhaseSixCommandAdapter delegate) {
        this.delegate = Objects.requireNonNull(delegate, "command delegate");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            String @NotNull [] arguments) {
        if (arguments.length != 0) {
            return false;
        }
        return delegate.onCommand(sender, command, label, arguments);
    }

    @Override
    public @NotNull List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            String @NotNull [] arguments) {
        return List.of();
    }
}
