package net.maddkraft.maddprestige.platform.paper.bootstrap;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Small production ingress for bootstrap status, provider diagnostics, and validated reload. */
final class PhaseSevenCommandExecutor implements CommandExecutor, TabCompleter {
    private static final List<String> ROOTS = List.of("status", "doctor", "providers", "reload");
    private final Supplier<List<String>> status;
    private final Supplier<List<String>> providers;
    private final Supplier<List<String>> reload;

    PhaseSevenCommandExecutor(Supplier<List<String>> status, Supplier<List<String>> providers,
            Supplier<List<String>> reload) {
        this.status = Objects.requireNonNull(status, "status");
        this.providers = Objects.requireNonNull(providers, "providers");
        this.reload = Objects.requireNonNull(reload, "reload");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, String @NotNull [] arguments) {
        String root = arguments.length == 0 ? "status" : arguments[0].toLowerCase(Locale.ROOT);
        List<String> lines = switch (root) {
            case "status", "doctor" -> status.get();
            case "providers" -> providers.get();
            case "reload" -> {
                if (!sender.hasPermission("maddprestige.admin.config.apply")) {
                    yield List.of("[permission.denied] Missing maddprestige.admin.config.apply.");
                }
                yield reload.get();
            }
            default -> List.of("[command.usage] /maddprestige [status|doctor|providers|reload]");
        };
        lines.forEach(line -> sender.sendMessage(Component.text(line)));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String alias, String @NotNull [] arguments) {
        if (arguments.length != 1) {
            return List.of();
        }
        String prefix = arguments[0].toLowerCase(Locale.ROOT);
        return ROOTS.stream().filter(value -> value.startsWith(prefix))
                .filter(value -> !"reload".equals(value)
                        || sender.hasPermission("maddprestige.admin.config.apply"))
                .toList();
    }
}
