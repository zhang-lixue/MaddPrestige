package net.maddkraft.qualification.phase8e.provider;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.metric.MetricMonotonicity;
import net.maddkraft.maddprestige.api.metric.MetricOperator;
import net.maddkraft.maddprestige.api.metric.MetricReadMode;
import net.maddkraft.maddprestige.api.metric.MetricResetPolicy;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.provider.ProviderCallContext;
import net.maddkraft.maddprestige.api.provider.ProviderDeclaration;
import net.maddkraft.maddprestige.api.provider.ProviderExecutionExpectation;
import net.maddkraft.maddprestige.api.provider.ProviderMetadata;
import net.maddkraft.maddprestige.api.provider.ProviderMetricDefinition;
import net.maddkraft.maddprestige.api.provider.ProviderRegistrationHandle;
import net.maddkraft.maddprestige.api.provider.RequirementProvider;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/** Shared mechanics for independently owned disposable qualification providers. */
public final class Phase8EProviderSupport {
    private Phase8EProviderSupport() {
    }

    public static ProviderMetricDefinition countMetric(String id, String unit) {
        return new ProviderMetricDefinition(new MetricId(id), MetricValueType.COUNT,
                Set.of(MetricOperator.GREATER_OR_EQUAL),
                Set.of(MetricReadMode.CURRENT, MetricReadMode.LIFETIME), false,
                MetricMonotonicity.MONOTONIC, MetricResetPolicy.NOT_APPLICABLE, Map.of(),
                "phase8e.metric." + id, "phase8e.metric." + id + ".description", unit, "authoritative");
    }

    public static void verifyContext(
            ProviderCallContext context,
            boolean identified,
            String ownerNamespace,
            String providerName) {
        boolean invalid = Bukkit.isPrimaryThread()
                || context.execution() != ProviderExecutionExpectation.BOUNDED_WORKER
                || !context.ownerNamespace().equals(ownerNamespace)
                || context.providerId().isPresent() != identified
                || context.cancellationRequested()
                || context.expired(Instant.now());
        if (invalid) throw new IllegalStateException(providerName + " received an invalid callback context");
    }

    public static ProviderDeclaration declaration(
            Function<ProviderCallContext, ProviderMetadata> metadata,
            RequirementProvider requirements,
            Consumer<ProviderRegistrationHandle> registered,
            Runnable unregistered) {
        return new ProviderDeclaration() {
            @Override
            public ProviderMetadata metadata(ProviderCallContext context) {
                return metadata.apply(context);
            }

            @Override
            public RequirementProvider requirements() {
                return requirements;
            }

            @Override
            public void registered(ProviderRegistrationHandle registration) {
                registered.accept(registration);
            }

            @Override
            public void unregistered() {
                unregistered.run();
            }
        };
    }

    public static ProviderDeclaration declaration(
            Profile profile,
            RequirementProvider requirements,
            Consumer<ProviderRegistrationHandle> registered,
            Runnable unregistered) {
        return declaration(profile::metadata, requirements, registered, unregistered);
    }

    public record Profile(
            String ownerNamespace,
            String providerName,
            String id,
            String displayName,
            String version,
            String unit,
            List<String> metrics) {
        public Profile {
            java.util.Objects.requireNonNull(ownerNamespace, "owner namespace");
            java.util.Objects.requireNonNull(providerName, "provider name");
            java.util.Objects.requireNonNull(id, "id");
            java.util.Objects.requireNonNull(displayName, "display name");
            java.util.Objects.requireNonNull(version, "version");
            java.util.Objects.requireNonNull(unit, "unit");
            metrics = List.copyOf(metrics);
        }

        public ProviderMetadata metadata(ProviderCallContext context) {
            verifyContext(context, false, ownerNamespace, providerName);
            return new ProviderMetadata(id, displayName, version,
                    metrics.stream().map(metric -> countMetric(metric, unit)).toList());
        }

        public void verifyReadContext(ProviderCallContext context) {
            verifyContext(context, true, ownerNamespace, providerName);
        }
    }

    public static final class Commands<M extends Enum<M>> {
        private final JavaPlugin plugin;
        private final String commandName;
        private final String logName;
        private final Class<M> modeType;
        private final AtomicReference<M> mode;
        private final Consumer<M> modeChanged;

        public Commands(
                JavaPlugin plugin,
                String commandName,
                String logName,
                Class<M> modeType,
                AtomicReference<M> mode,
                Consumer<M> modeChanged) {
            this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
            this.commandName = java.util.Objects.requireNonNull(commandName, "command name");
            this.logName = java.util.Objects.requireNonNull(logName, "log name");
            this.modeType = java.util.Objects.requireNonNull(modeType, "mode type");
            this.mode = java.util.Objects.requireNonNull(mode, "mode");
            this.modeChanged = java.util.Objects.requireNonNull(modeChanged, "mode changed");
        }

        public boolean execute(
                CommandSender sender,
                Command command,
                String[] arguments,
                BiFunction<String, CommandSender, Boolean> extension) {
            if (!command.getName().equalsIgnoreCase(commandName) || arguments.length == 0) return false;
            try {
                String action = arguments[0].toLowerCase(Locale.ROOT);
                if ("mode".equals(action)) {
                    if (arguments.length != 2) return false;
                    M replacement = Enum.valueOf(modeType, arguments[1].toUpperCase(Locale.ROOT));
                    mode.set(replacement);
                    modeChanged.accept(replacement);
                    return true;
                }
                return extension.apply(action, sender);
            } catch (RuntimeException failure) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, logName + " command failed", failure);
                return false;
            }
        }
    }

    public static final class Registration {
        private final JavaPlugin plugin;
        private final String logName;
        private final Supplier<ProviderDeclaration> declarations;
        private ProviderDeclaration primary;
        private ProviderDeclaration duplicate;
        private volatile ProviderRegistrationHandle handle;

        public Registration(JavaPlugin plugin, String logName, Supplier<ProviderDeclaration> declarations) {
            this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
            this.logName = java.util.Objects.requireNonNull(logName, "log name");
            this.declarations = java.util.Objects.requireNonNull(declarations, "declarations");
        }

        public void enable() {
            primary = declarations.get();
            register(primary, ServicePriority.Normal);
        }

        public void disable() {
            plugin.getServer().getServicesManager().unregisterAll(plugin);
        }

        public void rebind() {
            clearDuplicate();
            if (primary != null) {
                plugin.getServer().getServicesManager().unregister(ProviderDeclaration.class, primary);
            }
            handle = null;
            enable();
            plugin.getLogger().info(logName + " ServicesManager generation replaced");
        }

        public void registerDuplicate() {
            if (duplicate != null) return;
            duplicate = declarations.get();
            register(duplicate, ServicePriority.Low);
            plugin.getLogger().info(logName + " duplicate declaration submitted");
        }

        public void clearDuplicate() {
            if (duplicate == null) return;
            plugin.getServer().getServicesManager().unregister(ProviderDeclaration.class, duplicate);
            duplicate = null;
        }

        public ProviderRegistrationHandle requireHandle(String message) {
            ProviderRegistrationHandle current = handle;
            if (current == null) throw new IllegalStateException(message);
            return current;
        }

        public boolean hasHandle() {
            return handle != null;
        }

        public void registered(ProviderRegistrationHandle registration) {
            handle = java.util.Objects.requireNonNull(registration, "registration");
        }

        private void register(ProviderDeclaration declaration, ServicePriority priority) {
            plugin.getServer().getServicesManager().register(
                    ProviderDeclaration.class, declaration, plugin, priority);
        }
    }
}
