package net.maddkraft.qualification.phase8e;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
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
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Stateful Phase 8E mechanics shared without obscuring the owning scenarios. */
final class Phase8EQualificationSupport {
    static final String COMMAND_NAME = "maddprestige";

    private Phase8EQualificationSupport() {
    }

    static Object defaultValue(Class<?> type) {
        if (type == void.class) return null;
        if (type == List.class) return List.of();
        if (type == Set.class) return Set.of();
        if (type == Optional.class) return Optional.empty();
        if (!type.isPrimitive()) return null;
        return Array.get(Array.newInstance(type, 1), 0);
    }

    static String extract(Pattern pattern, List<String> lines) {
        return pattern.matcher(String.join("\n", lines)).results().findFirst()
                .map(MatchResult::group)
                .orElseThrow(() -> new QualificationFailure("expected identity absent from " + lines));
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new QualificationFailure(message);
    }

    static final class Coordinator {
        private final JavaPlugin plugin;
        private final Queue<Runnable> steps;
        private final List<String> messages;
        private final boolean includeDiagnosticCode;
        private final Consumer<String> transcript;
        private final BiConsumer<String, Throwable> fail;
        private final CommandSender sender;

        Coordinator(
                JavaPlugin plugin,
                Queue<Runnable> steps,
                List<String> messages,
                String identity,
                boolean includeDiagnosticCode,
                Consumer<String> transcript,
                BiConsumer<String, Throwable> fail) {
            this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
            this.steps = java.util.Objects.requireNonNull(steps, "steps");
            this.messages = java.util.Objects.requireNonNull(messages, "messages");
            this.includeDiagnosticCode = includeDiagnosticCode;
            this.transcript = java.util.Objects.requireNonNull(transcript, "transcript");
            this.fail = java.util.Objects.requireNonNull(fail, "fail");
            InvocationHandler handler = new SenderInvocation(plugin, messages, identity);
            sender = (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                    new Class<?>[] {CommandSender.class}, handler);
        }

        CommandSender sender() {
            return sender;
        }

        Runnable commandStep(Supplier<String> command, String label, Consumer<String> pass) {
            return () -> command(command.get(), _ -> {
                pass.accept(label);
                advance();
            });
        }

        void command(String command, Consumer<List<String>> continuation) {
            messages.clear();
            List<String> tokens = List.of(command.split(" +"));
            require(!tokens.isEmpty() && COMMAND_NAME.equals(tokens.getFirst()), "unexpected command ingress");
            var registered = plugin.getServer().getPluginCommand(COMMAND_NAME);
            String[] arguments = tokens.subList(1, tokens.size()).toArray(String[]::new);
            require(registered != null && registered.execute(sender, COMMAND_NAME, arguments),
                    "command rejected: " + command);
            eventually("command response " + command, Duration.ofSeconds(30), () -> !messages.isEmpty(), () -> {
                List<String> snapshot = List.copyOf(messages);
                transcript.accept(command + " -> " + snapshot);
                require(snapshot.stream().noneMatch(this::failureDiagnostic),
                        "command failed: " + command + " -> " + snapshot);
                continuation.accept(snapshot);
            });
        }

        boolean failureDiagnostic(String line) {
            String normalized = line.toLowerCase(java.util.Locale.ROOT);
            return normalized.contains("[error]") || normalized.contains("internal failure")
                    || normalized.contains("command failed")
                    || includeDiagnosticCode && normalized.contains("diagnostic code:")
                    || normalized.startsWith("usage:");
        }

        <T> void await(String label, CompletionStage<T> stage, Consumer<T> continuation) {
            stage.handle((value, failure) -> {
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> complete(label, value, failure, continuation));
                return null;
            });
        }

        private <T> void complete(
                String label,
                T value,
                Throwable failure,
                Consumer<T> continuation) {
            if (failure != null) {
                fail.accept(label, failure);
            } else {
                safely(label, () -> continuation.accept(value));
            }
        }

        void eventually(String label, BooleanSupplier condition, Runnable continuation) {
            eventually(label, Duration.ofSeconds(30), condition, continuation);
        }

        void eventually(
                String label,
                Duration timeout,
                BooleanSupplier condition,
                Runnable continuation) {
            new Poll(label, timeout, condition, continuation).start();
        }

        void advance() {
            Optional.ofNullable(steps.poll()).ifPresent(next -> plugin.getServer().getScheduler()
                    .runTaskLater(plugin, () -> safely("qualification step", next), 1L));
        }

        private void safely(String label, Runnable action) {
            try {
                action.run();
            } catch (RuntimeException | LinkageError failure) {
                fail.accept(label, failure);
            }
        }

        private final class Poll implements Runnable {
            private final String label;
            private final long deadline;
            private final BooleanSupplier condition;
            private final Runnable continuation;
            private BukkitTask task;

            private Poll(String label, Duration timeout, BooleanSupplier condition, Runnable continuation) {
                this.label = label;
                deadline = System.nanoTime() + timeout.toNanos();
                this.condition = condition;
                this.continuation = continuation;
            }

            private void start() {
                task = plugin.getServer().getScheduler().runTaskTimer(plugin, this, 1L, 1L);
            }

            @Override
            public void run() {
                try {
                    if (condition.getAsBoolean()) {
                        finish(continuation);
                    } else if (System.nanoTime() >= deadline) {
                        finish(() -> fail.accept(label, new QualificationFailure("timed out")));
                    }
                } catch (RuntimeException | LinkageError failure) {
                    finish(() -> fail.accept(label, failure));
                }
            }

            private void finish(Runnable completion) {
                task.cancel();
                completion.run();
            }
        }
    }

    private record SenderInvocation(
            JavaPlugin plugin,
            List<String> messages,
            String identity) implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            String name = method.getName();
            if ("sendMessage".equals(name) && arguments != null) {
                Arrays.stream(arguments).forEach(this::capture);
                return null;
            }
            return switch (name) {
                case "hasPermission", "isPermissionSet", "isOp" -> true;
                case "getName" -> identity;
                case "getServer" -> plugin.getServer();
                case "spigot" -> new CommandSender.Spigot();
                case "toString" -> identity + "-CommandSender";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> defaultValue(method.getReturnType());
            };
        }

        private void capture(Object argument) {
            switch (argument) {
                case Component component -> messages.add(
                        PlainTextComponentSerializer.plainText().serialize(component));
                case String text -> messages.add(text);
                case String[] lines -> messages.addAll(List.of(lines));
                default -> { /* Other overload payloads do not carry user-visible text. */ }
            }
        }
    }

    static final class QualificationFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private QualificationFailure(String message) {
            super(message);
        }
    }
}
