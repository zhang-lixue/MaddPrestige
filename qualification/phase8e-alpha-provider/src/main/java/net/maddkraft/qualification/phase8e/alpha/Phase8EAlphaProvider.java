package net.maddkraft.qualification.phase8e.alpha;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.metric.MetricMonotonicity;
import net.maddkraft.maddprestige.api.metric.MetricOperator;
import net.maddkraft.maddprestige.api.metric.MetricReadMode;
import net.maddkraft.maddprestige.api.metric.MetricResetPolicy;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.provider.ProviderCallContext;
import net.maddkraft.maddprestige.api.provider.ProviderDeclaration;
import net.maddkraft.maddprestige.api.provider.ProviderExecutionExpectation;
import net.maddkraft.maddprestige.api.provider.ProviderMetadata;
import net.maddkraft.maddprestige.api.provider.ProviderMetricDefinition;
import net.maddkraft.maddprestige.api.provider.ProviderMetricRequest;
import net.maddkraft.maddprestige.api.provider.ProviderMetricResult;
import net.maddkraft.maddprestige.api.provider.ProviderRegistrationHandle;
import net.maddkraft.maddprestige.api.provider.RequirementProvider;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/** Independently owned, mode-controlled Stable provider used only in disposable Phase 8E servers. */
public final class Phase8EAlphaProvider extends JavaPlugin {
    private final AtomicReference<Mode> mode = new AtomicReference<>(Mode.HEALTHY);
    private final AtomicInteger reads = new AtomicInteger();
    private final AtomicInteger registrations = new AtomicInteger();
    private final AtomicInteger cancellations = new AtomicInteger();
    private final AtomicInteger synchronousActive = new AtomicInteger();
    private final AtomicInteger synchronousHighWater = new AtomicInteger();
    private final AtomicInteger synchronousCompleted = new AtomicInteger();
    private final AtomicReference<CountDownLatch> synchronousRelease = new AtomicReference<>(new CountDownLatch(0));
    private ScheduledExecutorService observer;
    private Declaration primary;
    private Declaration duplicate;
    private volatile ProviderRegistrationHandle handle;

    @Override
    public void onEnable() {
        observer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon(true).name("phase8e-alpha-observer").factory());
        registerPrimary();
        getLogger().info("PHASE8E-ALPHA enabled after MaddPrestige startup");
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (observer != null) {
            observer.shutdownNow();
        }
        getLogger().info("PHASE8E-ALPHA disabled reads=" + reads.get() + " cancellations=" + cancellations.get());
    }

    /** Qualification observation exposed without coupling the harness to this plugin's implementation type. */
    public int observedCancellations() {
        return cancellations.get();
    }

    public int observedReads() {
        return reads.get();
    }

    public int observedRegistrations() {
        return registrations.get();
    }

    public int observedSynchronousActive() {
        return synchronousActive.get();
    }

    public int observedSynchronousHighWater() {
        return synchronousHighWater.get();
    }

    public int observedSynchronousCompleted() {
        return synchronousCompleted.get();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!command.getName().equalsIgnoreCase("phase8ealpha") || arguments.length == 0) {
            return false;
        }
        try {
            switch (arguments[0].toLowerCase(Locale.ROOT)) {
                case "mode" -> {
                    if (arguments.length != 2) return false;
                    Mode replacement = Mode.valueOf(arguments[1].toUpperCase(Locale.ROOT));
                    mode.set(replacement);
                    getLogger().info("PHASE8E-ALPHA mode=" + replacement);
                }
                case "unregister" -> {
                    ProviderRegistrationHandle current = requireHandle();
                    current.unregister().whenComplete((ignored, failure) -> getLogger().info(
                            "PHASE8E-ALPHA handle-unregister=" + (failure == null ? "complete" : "failed")));
                }
                case "rebind" -> rebind();
                case "duplicate" -> registerDuplicate();
                case "clear-duplicate" -> clearDuplicate();
                case "status" -> sender.sendMessage("mode=" + mode.get() + " reads=" + reads.get()
                        + " cancellations=" + cancellations.get() + " handle=" + (handle != null)
                        + " syncActive=" + synchronousActive.get() + " syncHighWater="
                        + synchronousHighWater.get() + " syncCompleted=" + synchronousCompleted.get());
                case "sync-reset" -> resetSynchronousBlock();
                case "sync-release" -> synchronousRelease.get().countDown();
                default -> {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException failure) {
            getLogger().log(java.util.logging.Level.SEVERE, "PHASE8E-ALPHA command failed", failure);
            return false;
        }
    }

    private void registerPrimary() {
        primary = new Declaration();
        getServer().getServicesManager().register(ProviderDeclaration.class, primary, this, ServicePriority.Normal);
    }

    private void rebind() {
        clearDuplicate();
        if (primary != null) {
            getServer().getServicesManager().unregister(ProviderDeclaration.class, primary);
        }
        handle = null;
        registerPrimary();
        getLogger().info("PHASE8E-ALPHA ServicesManager generation replaced");
    }

    private void registerDuplicate() {
        if (duplicate == null) {
            duplicate = new Declaration();
            getServer().getServicesManager().register(ProviderDeclaration.class, duplicate, this,
                    ServicePriority.Low);
            getLogger().info("PHASE8E-ALPHA duplicate declaration submitted");
        }
    }

    private void clearDuplicate() {
        if (duplicate != null) {
            getServer().getServicesManager().unregister(ProviderDeclaration.class, duplicate);
            duplicate = null;
        }
    }

    private ProviderRegistrationHandle requireHandle() {
        ProviderRegistrationHandle value = handle;
        if (value == null) throw new IllegalStateException("Alpha provider handle is absent");
        return value;
    }

    private final class Declaration implements ProviderDeclaration {
        @Override
        public ProviderMetadata metadata(ProviderCallContext context) {
            verifyContext(context, false);
            return new ProviderMetadata("alpha", "phase8e.provider.alpha", "1.0.0", List.of(
                    definition("points"), definition("bonus")));
        }

        @Override
        public RequirementProvider requirements() {
            return Phase8EAlphaProvider.this::read;
        }

        @Override
        public void registered(ProviderRegistrationHandle registration) {
            handle = registration;
            int generation = registrations.incrementAndGet();
            getLogger().info("PHASE8E-ALPHA registered id=" + registration.providerId().value()
                    + " qualification-generation=" + generation);
        }

        @Override
        public void unregistered() {
            getLogger().info("PHASE8E-ALPHA unregistered callback");
        }
    }

    private CompletionStage<Map<ProviderMetricRequest, ProviderMetricResult>> read(
            ProviderCallContext context,
            UUID playerId,
            List<ProviderMetricRequest> queries) {
        verifyContext(context, true);
        reads.incrementAndGet();
        return switch (mode.get()) {
            case HEALTHY -> CompletableFuture.completedFuture(results(queries, MetricValue.count(10)));
            case UNAVAILABLE -> CompletableFuture.completedFuture(unavailable(queries));
            case THROW -> throw new IllegalStateException("controlled Alpha runtime failure");
            case LINKAGE -> throw new NoClassDefFoundError("controlled-alpha-linkage");
            case MISSING -> CompletableFuture.completedFuture(missing(queries));
            case EXTRA -> CompletableFuture.completedFuture(extra(queries));
            case NULL -> CompletableFuture.completedFuture(withNull(queries));
            case WRONG_TYPE -> CompletableFuture.completedFuture(results(queries, MetricValue.bool(true)));
            case HANG -> untilCancelled(context);
            case SYNC_BLOCK -> synchronouslyBlock(queries);
        };
    }

    private CompletionStage<Map<ProviderMetricRequest, ProviderMetricResult>> synchronouslyBlock(
            List<ProviderMetricRequest> queries) {
        int active = synchronousActive.incrementAndGet();
        synchronousHighWater.accumulateAndGet(active, Math::max);
        try {
            synchronousRelease.get().await();
            return CompletableFuture.completedFuture(results(queries, MetricValue.count(10)));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("controlled synchronous Alpha block was interrupted", interrupted);
        } finally {
            synchronousActive.decrementAndGet();
            synchronousCompleted.incrementAndGet();
        }
    }

    private void resetSynchronousBlock() {
        if (synchronousActive.get() != 0) {
            throw new IllegalStateException("Cannot reset the synchronous block while callbacks are active");
        }
        synchronousHighWater.set(0);
        synchronousCompleted.set(0);
        synchronousRelease.set(new CountDownLatch(1));
        getLogger().info("PHASE8E-ALPHA synchronous callback barrier reset");
    }

    private CompletionStage<Map<ProviderMetricRequest, ProviderMetricResult>> untilCancelled(
            ProviderCallContext context) {
        CompletableFuture<Map<ProviderMetricRequest, ProviderMetricResult>> result = new CompletableFuture<>();
        java.util.concurrent.ScheduledFuture<?> probe = observer.scheduleAtFixedRate(() -> {
            if (context.cancellationRequested() || context.expired(Instant.now())) {
                cancellations.incrementAndGet();
                result.completeExceptionally(new IllegalStateException("controlled Alpha observed cancellation"));
            }
        }, 0, 10, TimeUnit.MILLISECONDS);
        result.whenComplete((ignored, failure) -> probe.cancel(false));
        return result;
    }

    private static Map<ProviderMetricRequest, ProviderMetricResult> results(
            List<ProviderMetricRequest> queries,
            MetricValue value) {
        LinkedHashMap<ProviderMetricRequest, ProviderMetricResult> result = new LinkedHashMap<>();
        queries.forEach(query -> result.put(query, ProviderMetricResult.available(value, Instant.now())));
        return result;
    }

    private static Map<ProviderMetricRequest, ProviderMetricResult> unavailable(
            List<ProviderMetricRequest> queries) {
        LinkedHashMap<ProviderMetricRequest, ProviderMetricResult> result = new LinkedHashMap<>();
        queries.forEach(query -> result.put(query, ProviderMetricResult.unavailable(Instant.now(),
                "phase8e.alpha.unavailable", "phase8e.alpha.unavailable", Map.of())));
        return result;
    }

    private static Map<ProviderMetricRequest, ProviderMetricResult> missing(List<ProviderMetricRequest> queries) {
        return queries.stream().limit(Math.max(0, queries.size() - 1L)).collect(
                java.util.stream.Collectors.toMap(query -> query,
                        query -> ProviderMetricResult.available(MetricValue.count(10), Instant.now()),
                        (first, ignored) -> first, LinkedHashMap::new));
    }

    private static Map<ProviderMetricRequest, ProviderMetricResult> extra(List<ProviderMetricRequest> queries) {
        LinkedHashMap<ProviderMetricRequest, ProviderMetricResult> result = new LinkedHashMap<>(
                results(queries, MetricValue.count(10)));
        ProviderMetricRequest rogue = new ProviderMetricRequest(new MetricId("rogue"), MetricReadMode.CURRENT,
                Map.of());
        result.put(rogue, ProviderMetricResult.available(MetricValue.count(10), Instant.now()));
        return result;
    }

    private static Map<ProviderMetricRequest, ProviderMetricResult> withNull(List<ProviderMetricRequest> queries) {
        LinkedHashMap<ProviderMetricRequest, ProviderMetricResult> result = new LinkedHashMap<>(
                results(queries, MetricValue.count(10)));
        if (!queries.isEmpty()) result.put(queries.getFirst(), null);
        return result;
    }

    private static ProviderMetricDefinition definition(String id) {
        return new ProviderMetricDefinition(new MetricId(id), MetricValueType.COUNT,
                Set.of(MetricOperator.GREATER_OR_EQUAL),
                Set.of(MetricReadMode.CURRENT, MetricReadMode.LIFETIME), false,
                MetricMonotonicity.MONOTONIC, MetricResetPolicy.NOT_APPLICABLE, Map.of(),
                "phase8e.metric." + id, "phase8e.metric." + id + ".description", "points", "authoritative");
    }

    private static void verifyContext(ProviderCallContext context, boolean identified) {
        if (Bukkit.isPrimaryThread()
                || context.execution() != ProviderExecutionExpectation.BOUNDED_WORKER
                || !context.ownerNamespace().equals("phase8e_alpha")
                || context.providerId().isPresent() != identified
                || context.cancellationRequested()
                || context.expired(Instant.now())) {
            throw new IllegalStateException("Alpha received an invalid callback context");
        }
    }

    private enum Mode {
        HEALTHY,
        UNAVAILABLE,
        THROW,
        LINKAGE,
        MISSING,
        EXTRA,
        NULL,
        WRONG_TYPE,
        HANG,
        SYNC_BLOCK
    }
}
