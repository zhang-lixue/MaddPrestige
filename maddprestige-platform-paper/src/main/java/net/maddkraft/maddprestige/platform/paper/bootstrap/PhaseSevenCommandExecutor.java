package net.maddkraft.maddprestige.platform.paper.bootstrap;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import net.maddkraft.maddprestige.core.admin.presentation.MessageReference;
import net.maddkraft.maddprestige.platform.paper.i18n.PaperMessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Small production ingress for bootstrap status, provider diagnostics, and validated reload. */
final class PhaseSevenCommandExecutor implements CommandExecutor, TabCompleter {
    private static final List<String> ROOTS = List.of("status", "doctor", "providers", "reload");
    private final Supplier<List<MessageReference>> status;
    private final Supplier<List<MessageReference>> providers;
    private final Supplier<List<MessageReference>> reload;
    private final PaperMessageService messages;

    PhaseSevenCommandExecutor(Supplier<List<MessageReference>> status, Supplier<List<MessageReference>> providers,
            Supplier<List<MessageReference>> reload, PaperMessageService messages) {
        this.status = Objects.requireNonNull(status, "status");
        this.providers = Objects.requireNonNull(providers, "providers");
        this.reload = Objects.requireNonNull(reload, "reload");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, String @NotNull [] arguments) {
        String root = arguments.length == 0 ? "status" : arguments[0].toLowerCase(Locale.ROOT);
        List<MessageReference> lines = switch (root) {
            case "status", "doctor" -> status.get();
            case "providers" -> providers.get();
            case "reload" -> {
                if (!sender.hasPermission("maddprestige.admin.config.apply")) {
                    yield List.of(MessageReference.of("phase7.permission_denied",
                            "permission", "maddprestige.admin.config.apply"));
                }
                yield reload.get();
            }
            default -> List.of(MessageReference.of("phase7.usage"));
        };
        lines.forEach(line -> sender.sendMessage(messages.render(line)));
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
