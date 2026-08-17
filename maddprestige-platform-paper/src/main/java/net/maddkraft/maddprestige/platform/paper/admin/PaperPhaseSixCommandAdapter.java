package net.maddkraft.maddprestige.platform.paper.admin;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.maddkraft.maddprestige.core.admin.command.CommandCompletionService;
import net.maddkraft.maddprestige.core.admin.command.CommandInvocation;
import net.maddkraft.maddprestige.core.admin.command.PhaseSixCommandService;
import net.maddkraft.maddprestige.platform.paper.ExecutionThread;
import net.maddkraft.maddprestige.platform.paper.PaperTaskScheduler;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Thin Paper ingress; all command authority and business behavior remains in core services. */
public final class PaperPhaseSixCommandAdapter implements CommandExecutor, TabCompleter {
    private final PhaseSixCommandService commands;
    private final CommandCompletionService completion;
    private final PaperTaskScheduler scheduler;
    private final PaperPhaseSixGuiController guiController;

    public PaperPhaseSixCommandAdapter(
            PhaseSixCommandService commands,
            CommandCompletionService completion,
            PaperTaskScheduler scheduler) {
        this(commands, completion, scheduler, null);
    }

    public PaperPhaseSixCommandAdapter(
            PhaseSixCommandService commands,
            CommandCompletionService completion,
            PaperTaskScheduler scheduler,
            PaperPhaseSixGuiController guiController) {
        this.commands = Objects.requireNonNull(commands, "commands");
        this.completion = Objects.requireNonNull(completion, "completion");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.guiController = guiController;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            String @NotNull [] arguments) {
        var invocation = new CommandInvocation(PaperPermissionSubjects.from(sender), Arrays.asList(arguments));
        commands.execute(invocation).whenComplete((response, failure) -> scheduler.submit(
                ExecutionThread.PAPER_SERVER_THREAD, () -> {
                    if (failure != null) {
                        sender.sendMessage(Component.text("[command.failed] The command could not complete safely."));
                    } else {
                        if (response.guiView().isPresent() && sender instanceof org.bukkit.entity.Player player) {
                            if (guiController == null) {
                                sender.sendMessage(Component.text("[gui.unavailable] GUI controller is not bound."));
                            } else {
                                guiController.open(player, response.guiView().orElseThrow());
                            }
                        }
                        response.lines().forEach(line -> sender.sendMessage(Component.text(
                                "[" + response.code() + "] " + line)));
                    }
                    return null;
                }));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            String @NotNull [] arguments) {
        return completion.suggest(PaperPermissionSubjects.from(sender), Arrays.asList(arguments));
    }
}
