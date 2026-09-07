package net.maddkraft.maddprestige.platform.paper.admin;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.maddkraft.maddprestige.core.admin.presentation.MessageReference;
import net.maddkraft.maddprestige.core.admin.command.CommandCompletionService;
import net.maddkraft.maddprestige.core.admin.command.CommandInvocation;
import net.maddkraft.maddprestige.core.admin.command.AdministrationCommandService;
import net.maddkraft.maddprestige.platform.paper.ExecutionThread;
import net.maddkraft.maddprestige.platform.paper.PaperTaskScheduler;
import net.maddkraft.maddprestige.platform.paper.i18n.PaperMessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Thin Paper ingress; all command authority and business behavior remains in core services. */
public final class PaperAdministrationCommandAdapter implements CommandExecutor, TabCompleter {
    private static final Pattern PLAYER_SELECTOR = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private final AdministrationCommandService commands;
    private final CommandCompletionService completion;
    private final PaperTaskScheduler scheduler;
    private final PaperAdministrationGuiController guiController;
    private final PaperMessageService messages;

    public PaperAdministrationCommandAdapter(
            AdministrationCommandService commands,
            CommandCompletionService completion,
            PaperTaskScheduler scheduler,
            PaperMessageService messages) {
        this(commands, completion, scheduler, null, messages);
    }

    public PaperAdministrationCommandAdapter(
            AdministrationCommandService commands,
            CommandCompletionService completion,
            PaperTaskScheduler scheduler,
            PaperAdministrationGuiController guiController,
            PaperMessageService messages) {
        this.commands = Objects.requireNonNull(commands, "commands");
        this.completion = Objects.requireNonNull(completion, "completion");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.guiController = guiController;
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            String @NotNull [] arguments) {
        if (arguments.length > 0 && "locale".equals(arguments[0].toLowerCase(Locale.ROOT))) {
            return reloadLocale(sender, arguments);
        }
        var invocation = new CommandInvocation(PaperPermissionSubjects.from(sender), Arrays.asList(arguments));
        commands.execute(invocation).whenComplete((response, failure) -> scheduler.submit(
                ExecutionThread.PAPER_SERVER_THREAD, () -> {
                    try {
                        deliver(sender, response, failure);
                    } catch (RuntimeException exception) {
                        sender.sendMessage(messages.render("command.failed"));
                    }
                    return null;
                }));
        return true;
    }

    private void deliver(
            CommandSender sender,
            net.maddkraft.maddprestige.core.admin.command.CommandResponse response,
            Throwable failure) {
        if (failure != null) {
            sender.sendMessage(messages.render("command.failed"));
            return;
        }
        if (response.guiView().isPresent() && sender instanceof org.bukkit.entity.Player player) {
            if (guiController == null) {
                sender.sendMessage(messages.render("gui.unavailable"));
            } else {
                guiController.open(player, response.guiView().orElseThrow());
            }
        }
        renderResponse(response, messages).forEach(sender::sendMessage);
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            String @NotNull [] arguments) {
        if (arguments.length == 1 && "locale".startsWith(arguments[0].toLowerCase(Locale.ROOT))) {
            return java.util.stream.Stream.concat(
                    completion.suggest(PaperPermissionSubjects.from(sender), Arrays.asList(arguments)).stream(),
                    java.util.stream.Stream.of("locale")).distinct().sorted().toList();
        }
        if (arguments.length == 2 && "locale".equalsIgnoreCase(arguments[0])) {
            return sender.hasPermission("maddprestige.admin.locale.reload")
                    && "reload".startsWith(arguments[1].toLowerCase(Locale.ROOT))
                    ? List.of("reload") : List.of();
        }
        return completion.suggest(PaperPermissionSubjects.from(sender), Arrays.asList(arguments));
    }

    private boolean reloadLocale(CommandSender sender, String[] arguments) {
        if (arguments.length != 2 || !"reload".equalsIgnoreCase(arguments[1])) {
            sender.sendMessage(messages.render("locale.reload.usage"));
            return true;
        }
        if (!sender.hasPermission("maddprestige.admin.locale.reload")) {
            sender.sendMessage(messages.render("locale.reload.permission_denied"));
            return true;
        }
        var result = messages.reload();
        sender.sendMessage(messages.render(result.code(), Map.of(
                "locale", result.locale(), "detail", result.detail())));
        return true;
    }

    public static List<net.kyori.adventure.text.Component> renderResponse(
            net.maddkraft.maddprestige.core.admin.command.CommandResponse response,
            PaperMessageService messages) {
        Objects.requireNonNull(response, "command response");
        Objects.requireNonNull(messages, "messages");
        boolean playerStatusPresent = response.messages().stream().anyMatch(reference ->
                reference.key().equals("command.player.concise.ready")
                        || reference.key().equals("command.player.concise.not_ready"));
        return response.messages().stream()
                .filter(reference -> !playerStatusPresent
                        || !reference.key().equals("command.concise.ready")
                                && !reference.key().equals("command.concise.not_ready"))
                .map(reference -> renderReference(reference, messages)).toList();
    }

    private static Component renderReference(MessageReference reference, PaperMessageService messages) {
        if (reference.key().startsWith("command.history.entry.")
                || reference.key().startsWith("command.history.kind.")) {
            return renderHistoryEntry(reference, messages);
        }
        if (reference.key().equals("command.history.page")) {
            return renderHistoryPage(reference, messages);
        }
        if (reference.key().equals("command.config.draft_created")) {
            return renderDraftCreated(reference, messages);
        }
        if (reference.key().equals("command.config.draft_controls")) {
            return renderDraftControls(reference, messages);
        }
        if (!reference.key().equals("command.preview.confirmation_controls")) {
            return messages.render(reference);
        }
        String confirmation = reference.arguments().get("confirmation");
        if (confirmation == null || confirmation.isBlank()) {
            return messages.render(reference);
        }
        Component confirm = messages.render("command.preview.confirm_action")
                .clickEvent(ClickEvent.runCommand("/maddprestige confirm " + confirmation));
        Component copy = messages.render("command.preview.copy_action")
                .clickEvent(ClickEvent.copyToClipboard(confirmation));
        return confirm.append(Component.space()).append(copy).append(Component.space())
                .append(messages.render("command.preview.session_validity"));
    }

    private static Component renderDraftCreated(MessageReference reference, PaperMessageService messages) {
        Component created = messages.render(reference);
        String draft = reference.arguments().get("draft");
        if (draft == null || !uuid(draft)) {
            return created;
        }
        return created.append(Component.space()).append(draftAction(
                messages,
                "command.config.draft_copy_action",
                "command.config.draft_copy_hover",
                ClickEvent.copyToClipboard(draft)));
    }

    private static Component renderDraftControls(MessageReference reference, PaperMessageService messages) {
        String draft = reference.arguments().get("draft");
        if (draft == null || !uuid(draft)) {
            return messages.render(reference);
        }
        Component validate = draftAction(
                messages,
                "command.config.draft_validate_action",
                "command.config.draft_validate_hover",
                ClickEvent.runCommand("/maddprestige config validate " + draft));
        Component diff = draftAction(
                messages,
                "command.config.draft_diff_action",
                "command.config.draft_diff_hover",
                ClickEvent.runCommand("/maddprestige config diff " + draft));
        Component cancel = draftAction(
                messages,
                "command.config.draft_cancel_action",
                "command.config.draft_cancel_hover",
                ClickEvent.runCommand("/maddprestige config cancel " + draft));
        return validate.append(Component.space()).append(diff).append(Component.space()).append(cancel);
    }

    private static Component draftAction(
            PaperMessageService messages, String labelKey, String hoverKey, ClickEvent clickEvent) {
        return messages.render(labelKey)
                .clickEvent(clickEvent)
                .hoverEvent(HoverEvent.showText(messages.render(hoverKey)));
    }

    private static Component renderHistoryEntry(MessageReference reference, PaperMessageService messages) {
        Component rendered = messages.render(reference);
        String player = reference.arguments().get("player");
        String entry = reference.arguments().get("id");
        if (player == null || entry == null || !safePlayerSelector(player) || !uuid(entry)) {
            return rendered;
        }
        return rendered.clickEvent(ClickEvent.runCommand(
                "/maddprestige history details " + player + " " + entry));
    }

    private static Component renderHistoryPage(MessageReference reference, PaperMessageService messages) {
        Component page = messages.render(reference);
        String player = reference.arguments().get("player");
        if (player == null || !safePlayerSelector(player)) {
            return page;
        }
        OptionalInt previous = positivePage(reference.arguments().get("previous"));
        OptionalInt next = positivePage(reference.arguments().get("next"));
        Component result = Component.empty();
        if (previous.isPresent()) {
            result = result.append(messages.render("command.history.page.previous")
                    .clickEvent(ClickEvent.runCommand(historyPageCommand(player, previous.getAsInt()))))
                    .append(Component.text("   "));
        }
        result = result.append(page);
        if (next.isPresent()) {
            result = result.append(Component.text("   "))
                    .append(messages.render("command.history.page.next")
                            .clickEvent(ClickEvent.runCommand(historyPageCommand(player, next.getAsInt()))));
        }
        return result;
    }

    private static OptionalInt positivePage(String value) {
        if (value == null || value.isBlank()) {
            return OptionalInt.empty();
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 && Integer.toString(parsed).equals(value)
                    ? OptionalInt.of(parsed) : OptionalInt.empty();
        } catch (NumberFormatException exception) {
            return OptionalInt.empty();
        }
    }

    private static String historyPageCommand(String player, int page) {
        return "/maddprestige history " + player + " " + page;
    }

    private static boolean safePlayerSelector(String value) {
        return PLAYER_SELECTOR.matcher(value).matches() || uuid(value);
    }

    private static boolean uuid(String value) {
        try {
            return UUID.fromString(value).toString().equals(value.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
