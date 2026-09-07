package net.maddkraft.qualification.runtimefault.alpha;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.metric.MetricReadMode;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.provider.ProviderCallContext;
import net.maddkraft.maddprestige.api.provider.ProviderDeclaration;
import net.maddkraft.maddprestige.api.provider.ProviderMetricRequest;
import net.maddkraft.maddprestige.api.provider.ProviderMetricResult;
import net.maddkraft.maddprestige.api.provider.ProviderRegistrationHandle;
import net.maddkraft.qualification.runtimefault.provider.ProviderFixtureSupport;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/** Independently owned, mode-controlled Stable provider used only in disposable runtime provider/fault servers. */
public final class AlphaProviderPlugin extends JavaPlugin {
    private static final ProviderFixtureSupport.Profile PROFILE = new ProviderFixtureSupport.Profile(
            "qualification_alpha", "Alpha", "alpha", "qualification.provider.alpha", "1.0.0", "points",
            List.of("points", "bonus"));

    private final AtomicReference<Mode> mode = new AtomicReference<>(Mode.HEALTHY);
    private final AtomicInteger reads = new AtomicInteger();
    private final AtomicInteger registrations = new AtomicInteger();
    private final AtomicInteger cancellations = new AtomicInteger();
    private final AtomicInteger synchronousActive = new AtomicInteger();
    private final AtomicInteger synchronousHighWater = new AtomicInteger();
    private final AtomicInteger synchronousCompleted = new AtomicInteger();
    private final AtomicReference<CountDownLatch> synchronousRelease = new AtomicReference<>(new CountDownLatch(0));
    private ScheduledExecutorService observer;
    private ProviderFixtureSupport.Registration registration;
    private ProviderFixtureSupport.Commands<Mode> commands;

    @Override
    public void onEnable() {
        registration = new ProviderFixtureSupport.Registration(this, "RUNTIME-QUALIFICATION-ALPHA", this::declaration);
        commands = new ProviderFixtureSupport.Commands<>(this, "qualificationalpha", "RUNTIME-QUALIFICATION-ALPHA",
                Mode.class, mode, replacement -> getLogger().info("RUNTIME-QUALIFICATION-ALPHA mode=" + replacement));
        observer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon(true).name("runtime-qualification-alpha-observer").factory());
        registration.enable();
        getLogger().info("RUNTIME-QUALIFICATION-ALPHA enabled after MaddPrestige startup");
    }

    @Override
    public void onDisable() {
        registration.disable();
        if (observer != null) {
            observer.shutdownNow();
        }
        getLogger().info("RUNTIME-QUALIFICATION-ALPHA disabled reads=" + reads.get() + " cancellations=" + cancellations.get());
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
        return commands.execute(sender, command, arguments, this::providerCommand);
    }

    private boolean providerCommand(String action, CommandSender sender) {
        switch (action) {
            case "unregister" -> registration.requireHandle("Alpha provider handle is absent")
                    .unregister().whenComplete((ignored, failure) -> getLogger().info(
                            "RUNTIME-QUALIFICATION-ALPHA handle-unregister=" + (failure == null ? "complete" : "failed")));
            case "rebind" -> registration.rebind();
            case "duplicate" -> registration.registerDuplicate();
            case "clear-duplicate" -> registration.clearDuplicate();
            case "status" -> sender.sendMessage("mode=" + mode.get() + " reads=" + reads.get()
                    + " cancellations=" + cancellations.get() + " handle=" + registration.hasHandle()
                    + " syncActive=" + synchronousActive.get() + " syncHighWater="
                    + synchronousHighWater.get() + " syncCompleted=" + synchronousCompleted.get());
            case "sync-reset" -> resetSynchronousBlock();
            case "sync-release" -> synchronousRelease.get().countDown();
            default -> {
                return false;
            }
        }
        return true;
    }

    private ProviderDeclaration declaration() {
        return ProviderFixtureSupport.declaration(PROFILE, this::read, this::registered, this::unregistered);
    }

    private void registered(ProviderRegistrationHandle providerHandle) {
        registration.registered(providerHandle);
        int generation = registrations.incrementAndGet();
        getLogger().info("RUNTIME-QUALIFICATION-ALPHA registered id=" + providerHandle.providerId().value()
                + " qualification-generation=" + generation);
    }

    private void unregistered() {
        getLogger().info("RUNTIME-QUALIFICATION-ALPHA unregistered callback");
    }

    private CompletionStage<Map<ProviderMetricRequest, ProviderMetricResult>> read(
            ProviderCallContext context,
            UUID playerId,
            List<ProviderMetricRequest> queries) {
        PROFILE.verifyReadContext(context);
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
        getLogger().info("RUNTIME-QUALIFICATION-ALPHA synchronous callback barrier reset");
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
                "qualification.alpha.unavailable", "qualification.alpha.unavailable", Map.of())));
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
