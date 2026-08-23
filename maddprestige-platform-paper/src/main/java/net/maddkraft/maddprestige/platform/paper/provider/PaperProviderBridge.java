package net.maddkraft.maddprestige.platform.paper.provider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.regex.Pattern;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.metric.MetricDescriptor;
import net.maddkraft.maddprestige.api.metric.MetricDimension;
import net.maddkraft.maddprestige.api.metric.MetricProvider;
import net.maddkraft.maddprestige.api.metric.MetricQuery;
import net.maddkraft.maddprestige.api.metric.MetricSample;
import net.maddkraft.maddprestige.api.provider.CapabilityDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderCallContext;
import net.maddkraft.maddprestige.api.provider.ProviderCancellationSignal;
import net.maddkraft.maddprestige.api.provider.ProviderDeclaration;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderExecutionExpectation;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.provider.ProviderMetadata;
import net.maddkraft.maddprestige.api.provider.ProviderMetricDefinition;
import net.maddkraft.maddprestige.api.provider.ProviderMetricRequest;
import net.maddkraft.maddprestige.api.provider.ProviderMetricResult;
import net.maddkraft.maddprestige.api.provider.ProviderMetricStatus;
import net.maddkraft.maddprestige.api.provider.ProviderRegistrationHandle;
import net.maddkraft.maddprestige.core.provider.ProviderRegistration;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** Owner-attested Paper service bridge into the one internal provider registry. */
public final class PaperProviderBridge implements Listener, AutoCloseable {
    private static final Duration METADATA_DEADLINE = Duration.ofSeconds(2);
    private static final Duration READ_DEADLINE = Duration.ofSeconds(3);
    private static final Pattern NON_NAMESPACE = Pattern.compile("[^a-z0-9_]");

    private final Plugin plugin;
    private final ProviderRegistry registry;
    private final Clock clock;
    private final Function<Class<?>, Plugin> implementationOwners;
    private final ExecutorService callbacks;
    private final ExecutorService lifecycle;
    private final Map<ProviderDeclaration, Binding> bindings = new IdentityHashMap<>();
    private final Map<String, Plugin> namespaceOwners = new LinkedHashMap<>();
    private boolean closing;

    public PaperProviderBridge(Plugin plugin, ProviderRegistry registry, Clock clock) {
        this(plugin, registry, clock, type -> JavaPlugin.getProvidingPlugin(type));
    }

    PaperProviderBridge(
            Plugin plugin,
            ProviderRegistry registry,
            Clock clock,
            Function<Class<?>, Plugin> implementationOwners) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "provider registry");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.implementationOwners = Objects.requireNonNull(implementationOwners, "implementation owner resolver");
        callbacks = new ThreadPoolExecutor(2, 8, 60, TimeUnit.SECONDS, new SynchronousQueue<>(),
                Thread.ofPlatform().daemon(true).name("maddprestige-provider-callback-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
        lifecycle = java.util.concurrent.Executors.newSingleThreadExecutor(
                Thread.ofPlatform().daemon(true).name("maddprestige-provider-lifecycle").factory());
    }

    /** Starts lifecycle observation and asynchronously imports declarations that already exist. */
    public CompletionStage<Void> start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        return CompletableFuture.runAsync(() -> plugin.getServer().getServicesManager()
                .getRegistrations(ProviderDeclaration.class).forEach(this::register), lifecycle);
    }

    @EventHandler
    public void onServiceRegister(ServiceRegisterEvent event) {
        RegisteredServiceProvider<?> service = event.getProvider();
        if (service.getService() == ProviderDeclaration.class) {
            scheduleRegistration(cast(service));
        }
    }

    @EventHandler
    public void onServiceUnregister(ServiceUnregisterEvent event) {
        Object value = event.getProvider().getProvider();
        if (value instanceof ProviderDeclaration declaration) {
            scheduleUnregistration(declaration);
        }
    }

    /** Number of currently owner-attested live registrations. */
    public synchronized int size() {
        return bindings.size();
    }

    private void scheduleRegistration(RegisteredServiceProvider<ProviderDeclaration> service) {
        synchronized (this) {
            if (closing || bindings.containsKey(service.getProvider())) {
                return;
            }
        }
        try {
            lifecycle.execute(() -> register(service));
        } catch (RejectedExecutionException saturated) {
            logFailure(service.getPlugin(), "registration scheduling", saturated);
        }
    }

    private void register(RegisteredServiceProvider<ProviderDeclaration> service) {
        ProviderDeclaration declaration = service.getProvider();
        synchronized (this) {
            if (closing || bindings.containsKey(declaration)) {
                return;
            }
        }
        Plugin owner = service.getPlugin();
        if (owner == plugin || !owner.isEnabled()) {
            return;
        }
        Plugin implementationOwner;
        try {
            implementationOwner = implementationOwners.apply(declaration.getClass());
        } catch (RuntimeException | LinkageError failure) {
            logFailure(owner, "implementation owner attestation", failure);
            return;
        }
        if (implementationOwner != owner) {
            logFailure(owner, "implementation owner attestation",
                    new SecurityException("Service owner does not own the provider implementation class"));
            return;
        }
        String namespace;
        try {
            namespace = namespace(owner.getName());
        } catch (RuntimeException failure) {
            logFailure(owner, "owner namespace validation", failure);
            return;
        }
        synchronized (this) {
            Plugin existing = namespaceOwners.get(namespace);
            if (existing != null && existing != owner) {
                logFailure(owner, "owner namespace collision",
                        new SecurityException("Another legal plugin name normalizes to the same namespace"));
                return;
            }
        }
        CallbackContext callback = context(namespace, Optional.empty(), "metadata", METADATA_DEADLINE);
        ProviderMetadata metadata;
        try {
            metadata = bounded(() -> declaration.metadata(callback.context()), METADATA_DEADLINE,
                    callback.cancellation());
        } catch (RuntimeException | LinkageError failure) {
            logFailure(owner, "metadata", failure);
            return;
        }
        ProviderId providerId;
        List<MetricDescriptor> metrics;
        try {
            providerId = new ProviderId(namespace + ":" + metadata.localId());
            metrics = metadata.metrics().stream().map(metric -> adapt(metric, providerId)).toList();
        } catch (RuntimeException | LinkageError failure) {
            logFailure(owner, "metadata validation", failure);
            return;
        }
        ExternalMetricProvider provider = new ExternalMetricProvider(providerId, namespace, metadata, metrics,
                declaration, registry, clock, callbacks);
        ProviderRegistration registration;
        try {
            registration = registry.registerAttested(namespace, provider.descriptor(), provider, provider.health());
            registry.activate(registration);
        } catch (RuntimeException failure) {
            logFailure(owner, "registration", failure);
            return;
        }
        Handle handle = new Handle(providerId, declaration, registration);
        synchronized (this) {
            if (closing || bindings.containsKey(declaration)) {
                safeUnregister(registration);
                return;
            }
            namespaceOwners.put(namespace, owner);
            bindings.put(declaration, new Binding(owner, namespace, registration, handle));
        }
        provider.bind(registration);
        invokeIsolated(owner, "registered callback", () -> declaration.registered(handle));
    }

    private void scheduleUnregistration(ProviderDeclaration declaration) {
        try {
            lifecycle.execute(() -> unregister(declaration, true));
        } catch (RejectedExecutionException saturated) {
            plugin.getLogger().warning("Provider unregistration scheduling rejected during shutdown or saturation");
        }
    }

    private void unregister(ProviderDeclaration declaration, boolean notify) {
        Binding binding;
        synchronized (this) {
            binding = bindings.remove(declaration);
            if (binding != null && bindings.values().stream().noneMatch(value ->
                    value.owner() == binding.owner() && value.namespace().equals(binding.namespace()))) {
                namespaceOwners.remove(binding.namespace(), binding.owner());
            }
        }
        if (binding == null) {
            return;
        }
        binding.handle.invalidate();
        safeUnregister(binding.registration);
        if (notify) {
            invokeIsolated(binding.owner, "unregistered callback", declaration::unregistered);
        }
    }

    private void safeUnregister(ProviderRegistration registration) {
        try {
            registry.unregister(registration);
        } catch (IllegalStateException ignored) {
            // The exact generation is already absent.
        }
    }

    private void invokeIsolated(Plugin owner, String operation, Runnable callback) {
        try {
            bounded(() -> {
                callback.run();
                return Boolean.TRUE;
            }, METADATA_DEADLINE, new CancellationToken());
        } catch (RuntimeException | LinkageError failure) {
            logFailure(owner, operation, failure);
        }
    }

    private <T> T bounded(
            java.util.concurrent.Callable<T> action,
            Duration timeout,
            CancellationToken cancellation) {
        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
            try {
                return action.call();
            } catch (RuntimeException | LinkageError failure) {
                throw failure;
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }, callbacks).orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
        try {
            return Objects.requireNonNull(future.join(), "provider callback result");
        } finally {
            cancellation.cancel();
        }
    }

    private CallbackContext context(
            String ownerNamespace,
            Optional<ProviderId> providerId,
            String operation,
            Duration deadline) {
        CancellationToken cancellation = new CancellationToken();
        return new CallbackContext(new ProviderCallContext(UUID.randomUUID(), Instant.now(clock).plus(deadline),
                operation, ProviderExecutionExpectation.BOUNDED_WORKER, ownerNamespace, providerId, cancellation),
                cancellation);
    }

    private void logFailure(Plugin owner, String operation, Throwable failure) {
        plugin.getLogger().log(Level.WARNING, "Provider callback failed safely for " + owner.getName()
                + " during " + operation + ": " + failure.getClass().getSimpleName());
    }

    @Override
    public void close() {
        List<ProviderDeclaration> declarations;
        synchronized (this) {
            if (closing) {
                return;
            }
            closing = true;
            declarations = new ArrayList<>(bindings.keySet());
        }
        HandlerList.unregisterAll(this);
        declarations.forEach(declaration -> unregister(declaration, true));
        lifecycle.shutdown();
        awaitTermination(lifecycle, "provider lifecycle");
        callbacks.shutdown();
        awaitTermination(callbacks, "provider callbacks");
    }

    private void awaitTermination(ExecutorService executor, String name) {
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Timed out draining " + name + " during shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private static MetricDescriptor adapt(ProviderMetricDefinition metric, ProviderId providerId) {
        Map<String, MetricDimension> dimensions = metric.dimensions().entrySet().stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> new MetricDimension(
                        entry.getValue().id(), entry.getValue().required(), entry.getValue().allowedValues(),
                        entry.getValue().descriptionKey())));
        return new MetricDescriptor(providerId, metric.metricId(), metric.valueType(), metric.supportedOperators(),
                metric.supportedReads(), metric.snapshotDeltaSupported(), metric.monotonicity(), metric.resetPolicy(),
                dimensions, metric.displayNameKey(), metric.descriptionKey(), metric.unitCode(),
                metric.reliabilityCode());
    }

    private static String namespace(String pluginName) {
        String normalized = NON_NAMESPACE.matcher(pluginName.toLowerCase(java.util.Locale.ROOT)
                .replace('-', '_')).replaceAll("");
        if (normalized.isEmpty() || normalized.length() > 31) {
            throw new IllegalArgumentException("Plugin name cannot form a provider namespace");
        }
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private static RegisteredServiceProvider<ProviderDeclaration> cast(RegisteredServiceProvider<?> service) {
        return (RegisteredServiceProvider<ProviderDeclaration>) service;
    }

    private final class Handle implements ProviderRegistrationHandle {
        private final ProviderId providerId;
        private final ProviderDeclaration declaration;
        private final ProviderRegistration registration;
        private boolean valid = true;

        private Handle(
                ProviderId providerId,
                ProviderDeclaration declaration,
                ProviderRegistration registration) {
            this.providerId = providerId;
            this.declaration = declaration;
            this.registration = registration;
        }

        @Override
        public ProviderId providerId() {
            return providerId;
        }

        @Override
        public CompletionStage<Void> unregister() {
            synchronized (this) {
                if (!valid) {
                    return CompletableFuture.failedFuture(new IllegalStateException(
                            "Provider registration handle is stale"));
                }
            }
            return CompletableFuture.runAsync(() -> PaperProviderBridge.this.unregister(declaration, true), lifecycle);
        }

        private synchronized void invalidate() {
            valid = false;
        }
    }

    private record Binding(
            Plugin owner,
            String namespace,
            ProviderRegistration registration,
            Handle handle) {
    }

    private static final class ExternalMetricProvider implements MetricProvider {
        private final ProviderId id;
        private final ProviderDescriptor descriptor;
        private final List<MetricDescriptor> metrics;
        private final ProviderDeclaration declaration;
        private final ProviderRegistry registry;
        private final Clock clock;
        private final ExecutorService callbacks;
        private final AtomicReference<ProviderHealth> health;
        private volatile ProviderRegistration registration;

        private ExternalMetricProvider(
                ProviderId id,
                String owner,
                ProviderMetadata metadata,
                List<MetricDescriptor> metrics,
                ProviderDeclaration declaration,
                ProviderRegistry registry,
                Clock clock,
                ExecutorService callbacks) {
            this.id = id;
            this.metrics = List.copyOf(metrics);
            this.declaration = declaration;
            this.registry = registry;
            this.clock = clock;
            this.callbacks = callbacks;
            descriptor = new ProviderDescriptor(id, owner, "2-stable", metadata.implementationVersion(), List.of(),
                    metrics.stream().map(metric -> new CapabilityDescriptor(metric.metricId().value(),
                            "requirement-metric", metric.description(), Map.of())).toList());
            health = new AtomicReference<>(new ProviderHealth(ProviderHealthState.AVAILABLE,
                    "provider.available", "Owner-attested provider is available", Instant.now(clock)));
        }

        private void bind(ProviderRegistration value) {
            registration = value;
        }

        @Override
        public List<MetricDescriptor> metrics() {
            return metrics;
        }

        @Override
        public CompletionStage<Map<MetricQuery, MetricSample>> read(
                UUID playerId,
                List<MetricQuery> queries,
                long providerGeneration) {
            Objects.requireNonNull(playerId, "player ID");
            List<MetricQuery> boundedQueries = List.copyOf(Objects.requireNonNull(queries, "queries"));
            if (boundedQueries.size() > 256) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("Metric read batch is too large"));
            }
            LinkedHashMap<MetricQuery, ProviderMetricRequest> requests = new LinkedHashMap<>();
            boundedQueries.forEach(query -> requests.put(query,
                    new ProviderMetricRequest(query.metricId(), query.readMode(), query.filters())));
            CancellationToken cancellation = new CancellationToken();
            ProviderCallContext context = new ProviderCallContext(UUID.randomUUID(),
                    Instant.now(clock).plus(READ_DEADLINE), "requirements.read",
                    ProviderExecutionExpectation.BOUNDED_WORKER, descriptor.ownerIdentity(), Optional.of(id),
                    cancellation);
            CompletableFuture<Map<ProviderMetricRequest, ProviderMetricResult>> future;
            try {
                future = CompletableFuture.supplyAsync(() -> {
                    try {
                        return Objects.requireNonNull(declaration.requirements(), "requirement provider")
                                .read(context, playerId, List.copyOf(requests.values()));
                    } catch (RuntimeException | LinkageError failure) {
                        throw new java.util.concurrent.CompletionException(failure);
                    }
                }, callbacks).orTimeout(READ_DEADLINE.toMillis(), TimeUnit.MILLISECONDS)
                        .thenCompose(stage -> Objects.requireNonNull(stage, "provider completion stage"))
                        .toCompletableFuture().orTimeout(READ_DEADLINE.toMillis(), TimeUnit.MILLISECONDS);
                future.whenComplete((ignored, failure) -> cancellation.cancel());
            } catch (RuntimeException | LinkageError failure) {
                cancellation.cancel();
                return failed(failure);
            }
            return future.thenApply(result -> {
                Set<ProviderMetricRequest> requestedKeys = Set.copyOf(requests.values());
                if (result == null || result.size() != requestedKeys.size()
                        || !result.keySet().equals(requestedKeys)
                        || result.values().stream().anyMatch(Objects::isNull)) {
                    throw new IllegalStateException("Provider returned an invalid metric result map");
                }
                LinkedHashMap<MetricQuery, MetricSample> adapted = new LinkedHashMap<>();
                requests.forEach((query, request) -> {
                    ProviderMetricResult metric = result.get(request);
                    if (metric.status() == ProviderMetricStatus.AVAILABLE) {
                        MetricDescriptor definition = metrics.stream()
                                .filter(value -> value.metricId().equals(query.metricId())).findFirst()
                                .orElseThrow(() -> new IllegalStateException(
                                        "Provider returned a result for an undeclared metric"));
                        if (metric.value().orElseThrow().type() != definition.valueType()) {
                            throw new IllegalStateException("Provider returned a semantically inconsistent value");
                        }
                        adapted.put(query, MetricSample.available(metric.value().orElseThrow(), providerGeneration,
                                metric.observedAt(), id.value()));
                    } else {
                        adapted.put(query, MetricSample.unavailable(providerGeneration, metric.observedAt(),
                                id.value(), metric.code()));
                    }
                });
                healthy();
                return Map.copyOf(adapted);
            }).exceptionallyCompose(this::failed);
        }

        private <T> CompletionStage<T> failed(Throwable failure) {
            health.set(new ProviderHealth(ProviderHealthState.UNHEALTHY, "provider.callback_failed",
                    "Provider callback failed or exceeded its deadline", Instant.now(clock)));
            if (registration != null) {
                registry.refreshHealth(id);
            }
            return CompletableFuture.failedFuture(new IllegalStateException("Provider callback failed safely",
                    failure));
        }

        private void healthy() {
            health.set(new ProviderHealth(ProviderHealthState.AVAILABLE, "provider.available",
                    "Owner-attested provider is available", Instant.now(clock)));
            if (registration != null) {
                registry.refreshHealth(id);
            }
        }

        @Override
        public ProviderDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public ProviderHealth health() {
            return health.get();
        }
    }

    private record CallbackContext(ProviderCallContext context, CancellationToken cancellation) {
    }

    private static final class CancellationToken implements ProviderCancellationSignal {
        private final AtomicBoolean cancelled = new AtomicBoolean();

        @Override
        public boolean cancellationRequested() {
            return cancelled.get();
        }

        private void cancel() {
            cancelled.set(true);
        }
    }
}
