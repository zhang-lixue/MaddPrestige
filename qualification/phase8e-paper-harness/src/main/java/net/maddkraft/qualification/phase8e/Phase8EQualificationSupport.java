package net.maddkraft.qualification.phase8e;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Shared qualification mechanics; scenario intent remains in the owning harness. */
final class Phase8EQualificationSupport {
    static final String COMMAND_NAME = "maddprestige";

    private Phase8EQualificationSupport() {
    }

    static CommandSender commandSender(JavaPlugin plugin, List<String> messages, String identity) {
        return (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                new Class<?>[] {CommandSender.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("sendMessage") && arguments != null) {
                        captureMessages(messages, arguments);
                        return null;
                    }
                    return switch (method.getName()) {
                        case "hasPermission", "isPermissionSet", "isOp" -> true;
                        case "getName" -> identity;
                        case "getServer" -> plugin.getServer();
                        case "spigot" -> new CommandSender.Spigot();
                        case "toString" -> identity + "-CommandSender";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == arguments[0];
                        default -> defaultValue(method.getReturnType());
                    };
                });
    }

    private static void captureMessages(List<String> messages, Object[] arguments) {
        for (Object argument : arguments) {
            if (argument instanceof Component component) {
                messages.add(PlainTextComponentSerializer.plainText().serialize(component));
            } else if (argument instanceof String text) {
                messages.add(text);
            } else if (argument instanceof String[] lines) {
                messages.addAll(List.of(lines));
            }
        }
    }

    static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            if (type == List.class) return List.of();
            if (type == Set.class) return Set.of();
            if (type == Optional.class) return Optional.empty();
            return null;
        }
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        return 0D;
    }

    static Runnable commandStep(
            Supplier<String> command,
            String label,
            BiConsumer<String, Consumer<List<String>>> dispatcher,
            Consumer<String> pass,
            Runnable advance) {
        return () -> dispatcher.accept(command.get(), ignored -> {
            pass.accept(label);
            advance.run();
        });
    }

    static void dispatchCommand(
            CommandContext context,
            String command,
            Consumer<List<String>> continuation) {
        JavaPlugin plugin = context.plugin();
        List<String> messages = context.messages();
        messages.clear();
        String[] tokens = command.split(" +");
        require(tokens.length > 0 && tokens[0].equals(COMMAND_NAME), "unexpected command ingress");
        var registered = plugin.getServer().getPluginCommand(COMMAND_NAME);
        require(registered != null && registered.execute(context.sender(), COMMAND_NAME,
                Arrays.copyOfRange(tokens, 1, tokens.length)), "command rejected: " + command);
        eventually(plugin, "command response " + command, Duration.ofSeconds(30), () -> !messages.isEmpty(), () -> {
            List<String> snapshot = List.copyOf(messages);
            context.transcript().accept(command + " -> " + snapshot);
            require(snapshot.stream().noneMatch(line -> failureDiagnostic(line, context.includeDiagnosticCode())),
                    "command failed: " + command + " -> " + snapshot);
            continuation.accept(snapshot);
        }, context.fail());
    }

    record CommandContext(
            JavaPlugin plugin,
            CommandSender sender,
            List<String> messages,
            boolean includeDiagnosticCode,
            Consumer<String> transcript,
            BiConsumer<String, Throwable> fail) {
    }

    static boolean failureDiagnostic(String line, boolean includeDiagnosticCode) {
        String normalized = line.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("[error]") || normalized.contains("internal failure")
                || normalized.contains("command failed")
                || includeDiagnosticCode && normalized.contains("diagnostic code:")
                || normalized.startsWith("usage:");
    }

    static <T> void await(
            JavaPlugin plugin,
            String label,
            CompletionStage<T> stage,
            Consumer<T> continuation,
            BiConsumer<String, Throwable> fail) {
        stage.whenComplete((value, failure) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (failure != null) {
                fail.accept(label, failure);
                return;
            }
            try {
                continuation.accept(value);
            } catch (RuntimeException | LinkageError exception) {
                fail.accept(label, exception);
            }
        }));
    }

    static void eventually(
            JavaPlugin plugin,
            String label,
            Duration timeout,
            BooleanSupplier condition,
            Runnable continuation,
            BiConsumer<String, Throwable> fail) {
        long deadline = System.nanoTime() + timeout.toNanos();
        BukkitTask[] task = new BukkitTask[1];
        task[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            try {
                if (condition.getAsBoolean()) {
                    task[0].cancel();
                    continuation.run();
                } else if (System.nanoTime() >= deadline) {
                    task[0].cancel();
                    fail.accept(label, new QualificationFailure("timed out"));
                }
            } catch (RuntimeException | LinkageError failure) {
                task[0].cancel();
                fail.accept(label, failure);
            }
        }, 1L, 1L);
    }

    static void advance(
            JavaPlugin plugin,
            Queue<Runnable> steps,
            BiConsumer<String, Throwable> fail) {
        Runnable next = steps.poll();
        if (next == null) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            try {
                next.run();
            } catch (RuntimeException | LinkageError failure) {
                fail.accept("qualification step", failure);
            }
        }, 1L);
    }

    static String extract(Pattern pattern, List<String> lines) {
        Matcher matcher = pattern.matcher(String.join("\n", lines));
        if (!matcher.find()) throw new QualificationFailure("expected identity absent from " + lines);
        return matcher.group();
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new QualificationFailure(message);
    }

    static final class QualificationFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private QualificationFailure(String message) {
            super(message);
        }
    }
}
