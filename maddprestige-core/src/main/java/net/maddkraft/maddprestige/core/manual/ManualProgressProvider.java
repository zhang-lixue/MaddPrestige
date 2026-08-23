package net.maddkraft.maddprestige.core.manual;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.metric.MetricDescriptor;
import net.maddkraft.maddprestige.api.metric.MetricMonotonicity;
import net.maddkraft.maddprestige.api.metric.MetricOperator;
import net.maddkraft.maddprestige.api.metric.MetricProvider;
import net.maddkraft.maddprestige.api.metric.MetricQuery;
import net.maddkraft.maddprestige.api.metric.MetricReadMode;
import net.maddkraft.maddprestige.api.metric.MetricResetPolicy;
import net.maddkraft.maddprestige.api.metric.MetricSample;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.provider.CapabilityDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;

public final class ManualProgressProvider implements MetricProvider {
    private static final int MAXIMUM_WRITE_BATCH = 1024;
    private static final MetricValue ZERO_COUNT = MetricValue.count(0);
    private final ProviderId providerId;
    private final String ownerIdentity;
    private final UUID ownerCapability;
    private final ManualProgressRepository repository;
    private final Executor writerExecutor;
    private final Clock clock;
    private final int maximumMetrics;
    private final int maximumEntries;
    private final AtomicReference<ProviderHealth> health;
    private final Map<MetricId, RegisteredMetric> metrics = new LinkedHashMap<>();
    private final Map<ProgressKey, ManualProgressRecord> values = new LinkedHashMap<>();
    private final Map<ProgressKey, ManualProgressRecord> dirty = new LinkedHashMap<>();
    private long nextGeneration;
    private boolean closing;
    private CompletableFuture<Integer> flushInFlight;

    private ManualProgressProvider(
            ProviderId providerId,
            String ownerIdentity,
            UUID ownerCapability,
            ManualProgressRepository repository,
            Executor writerExecutor,
            Clock clock,
            int maximumMetrics,
            int maximumEntries) {
        this.providerId = Objects.requireNonNull(providerId, "provider ID");
        this.ownerIdentity = Objects.requireNonNull(ownerIdentity, "owner identity");
        this.ownerCapability = Objects.requireNonNull(ownerCapability, "owner capability");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.writerExecutor = Objects.requireNonNull(writerExecutor, "writer executor");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (maximumMetrics < 1 || maximumMetrics > 4096 || maximumEntries < 1 || maximumEntries > 1_000_000) {
            throw new IllegalArgumentException("Manual progress bounds are outside the safe domain");
        }
        this.maximumMetrics = maximumMetrics;
        this.maximumEntries = maximumEntries;
        health = new AtomicReference<>(new ProviderHealth(ProviderHealthState.AVAILABLE, "manual.available",
                "Manual progress persistence is healthy", clock.instant()));
    }

    public static ManualProgressBootstrap bootstrap(
            ProviderId providerId,
            String ownerIdentity,
            ManualProgressRepository repository,
            Executor writerExecutor,
            Clock clock,
            int maximumMetrics,
            int maximumEntries) {
        UUID capability = UUID.randomUUID();
        ManualProgressProvider provider = new ManualProgressProvider(providerId, ownerIdentity, capability,
                repository, writerExecutor, clock, maximumMetrics, maximumEntries);
        return new ManualProgressBootstrap(provider, new ManualProgressOwner(provider, capability));
    }

    /** Human-readable identities are metadata, never registration authority. */
    public CompletionStage<ManualMetricRegistration> registerMetric(
            String callerIdentity,
            ManualCounterDefinition definition) {
        Objects.requireNonNull(callerIdentity, "caller identity");
        Objects.requireNonNull(definition, "definition");
        throw new SecurityException("Manual metric registration requires the opaque bootstrap capability");
    }

    CompletionStage<ManualMetricRegistration> registerMetric(
            UUID capability,
            ManualCounterDefinition definition) {
        requireCapability(capability);
        Objects.requireNonNull(definition, "definition");
        synchronized (this) {
            if (closing) {
                throw new IllegalStateException("Manual progress provider is closing");
            }
            if (metrics.containsKey(definition.metricId())) {
                throw new IllegalArgumentException("Manual metric is already registered: "
                        + definition.metricId().value());
            }
            if (metrics.size() >= maximumMetrics) {
                throw new IllegalStateException("Manual metric registration limit reached");
            }
        }
        return CompletableFuture.supplyAsync(() -> repository.load(providerId, definition.metricId()), writerExecutor)
                .thenApply(restored -> finishRegistration(definition, restored));
    }

    synchronized ProgressProvenance attest(UUID capability, String source, Instant observedAt) {
        requireCapability(capability);
        if (closing) {
            throw new SecurityException("Manual progress provider is closing");
        }
        return new ProgressProvenance(ownerIdentity, source, observedAt, ownerCapability);
    }

    public synchronized MetricValue increment(
            ManualMetricRegistration registration,
            UUID playerId,
            MetricValue amount,
            ProgressProvenance provenance) {
        RegisteredMetric metric = requireRegistration(registration, provenance);
        if (!metric.definition.incrementAllowed()) {
            throw new SecurityException("This manual metric does not permit increments");
        }
        requireType(metric.definition, amount);
        if (amount.asNumber().signum() < 0) {
            throw new IllegalArgumentException("Manual counter increments cannot be negative");
        }
        ProgressKey key = new ProgressKey(metric.definition.metricId(), playerId);
        MetricValue current = Optional.ofNullable(values.get(key)).map(ManualProgressRecord::value)
                .orElseGet(() -> zero(metric.definition));
        return update(key, metric.definition, MetricValue.fromNumber(amount.type(),
                current.asNumber().add(amount.asNumber())), provenance);
    }

    public synchronized MetricValue set(
            ManualMetricRegistration registration,
            UUID playerId,
            MetricValue value,
            ProgressProvenance provenance) {
        RegisteredMetric metric = requireRegistration(registration, provenance);
        if (!metric.definition.exactSetAllowed()) {
            throw new SecurityException("This manual metric does not permit exact set");
        }
        requireType(metric.definition, value);
        return update(new ProgressKey(metric.definition.metricId(), playerId), metric.definition, value, provenance);
    }

    public CompletionStage<Integer> flushAsync() {
        CompletableFuture<Integer> future;
        synchronized (this) {
            if (flushInFlight != null) {
                return flushInFlight;
            }
            if (dirty.isEmpty()) {
                return CompletableFuture.completedFuture(0);
            }
            future = new CompletableFuture<>();
            flushInFlight = future;
        }
        try {
            writerExecutor.execute(() -> drainDirty(future));
        } catch (RuntimeException failure) {
            synchronized (this) {
                if (flushInFlight == future) {
                    flushInFlight = null;
                }
            }
            persistenceStalled();
            future.completeExceptionally(failure);
        }
        return future;
    }

    public CompletionStage<Integer> closeAsync() {
        synchronized (this) {
            closing = true;
        }
        return flushAsync();
    }

    private void drainDirty(CompletableFuture<Integer> future) {
        int written = 0;
        try {
            while (true) {
                Map<ProgressKey, ManualProgressRecord> snapshot;
                synchronized (this) {
                    snapshot = dirty.entrySet().stream().limit(MAXIMUM_WRITE_BATCH).collect(
                            java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
                    if (snapshot.isEmpty()) {
                        if (flushInFlight == future) {
                            flushInFlight = null;
                        }
                        persistenceRecovered();
                        future.complete(written);
                        return;
                    }
                }
                repository.writeBatch(snapshot.values());
                written = Math.addExact(written, snapshot.size());
                synchronized (this) {
                    snapshot.forEach((key, record) -> dirty.computeIfPresent(key,
                            (ignored, current) -> current.updateVersion() == record.updateVersion() ? null : current));
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            synchronized (this) {
                if (flushInFlight == future) {
                    flushInFlight = null;
                }
            }
            persistenceStalled();
            future.completeExceptionally(failure);
        }
    }

    @Override
    public synchronized Collection<MetricDescriptor> metrics() {
        return metrics.values().stream().map(registered -> descriptor(registered.definition)).toList();
    }

    @Override
    public synchronized CompletionStage<Map<MetricQuery, MetricSample>> read(
            UUID playerId,
            List<MetricQuery> queries,
            long providerGeneration) {
        if (providerGeneration < 1) {
            throw new IllegalArgumentException("Provider generation must be positive");
        }
        LinkedHashMap<MetricQuery, MetricSample> result = new LinkedHashMap<>();
        Instant now = clock.instant();
        for (MetricQuery query : queries) {
            RegisteredMetric metric = metrics.get(query.metricId());
            if (metric == null || !query.filters().isEmpty()) {
                result.put(query, MetricSample.unavailable(providerGeneration, now, ownerIdentity,
                        "Manual metric is unregistered or does not support filters"));
                continue;
            }
            MetricValue value = Optional.ofNullable(values.get(new ProgressKey(query.metricId(), playerId)))
                    .map(ManualProgressRecord::value).orElseGet(() -> zero(metric.definition));
            result.put(query, MetricSample.available(value, providerGeneration, now, ownerIdentity + ":manual"));
        }
        return CompletableFuture.completedFuture(Map.copyOf(result));
    }

    @Override
    public synchronized ProviderDescriptor descriptor() {
        List<CapabilityDescriptor> capabilities = metrics.values().stream()
                .map(metric -> new CapabilityDescriptor(metric.definition.metricId().value(), "manual-progress",
                        metric.definition.description(), Map.of("value-type", metric.definition.valueType().name(),
                                "owner", ownerIdentity))).toList();
        return new ProviderDescriptor(providerId, ownerIdentity, "phase3-foundation", "1",
                List.of(), capabilities);
    }

    @Override
    public ProviderHealth health() {
        return health.get();
    }

    private void persistenceStalled() {
        health.updateAndGet(current -> current.state() == ProviderHealthState.DEGRADED
                && "manual.persistence_stalled".equals(current.code()) ? current
                        : new ProviderHealth(ProviderHealthState.DEGRADED, "manual.persistence_stalled",
                                "Dirty manual progress could not be durably flushed", clock.instant()));
    }

    private void persistenceRecovered() {
        health.updateAndGet(current -> current.state() == ProviderHealthState.AVAILABLE
                && "manual.available".equals(current.code()) ? current
                        : new ProviderHealth(ProviderHealthState.AVAILABLE, "manual.available",
                                "Manual progress persistence recovered", clock.instant()));
    }

    private synchronized ManualMetricRegistration finishRegistration(
            ManualCounterDefinition definition,
            Map<UUID, ManualProgressRecord> restored) {
        if (closing) {
            throw new IllegalStateException("Manual progress provider closed during metric registration");
        }
        if (metrics.containsKey(definition.metricId())) {
            throw new IllegalArgumentException("Manual metric registration raced with another registration");
        }
        if (metrics.size() >= maximumMetrics) {
            throw new IllegalStateException("Manual metric registration limit reached");
        }
        if (values.size() + restored.size() > maximumEntries) {
            throw new IllegalStateException("Restored manual progress exceeds the configured entry bound");
        }
        long generation = Math.addExact(nextGeneration, 1);
        nextGeneration = generation;
        UUID token = UUID.randomUUID();
        metrics.put(definition.metricId(), new RegisteredMetric(definition, generation, token));
        restored.forEach((player, record) -> {
            requireType(definition, record.value());
            values.put(new ProgressKey(definition.metricId(), player), record);
        });
        return new ManualMetricRegistration(providerId, definition.metricId(), ownerIdentity, generation, token);
    }

    private RegisteredMetric requireRegistration(
            ManualMetricRegistration registration,
            ProgressProvenance provenance) {
        Objects.requireNonNull(registration, "registration");
        Objects.requireNonNull(provenance, "provenance");
        RegisteredMetric current = metrics.get(registration.metricId());
        if (closing || current == null || !providerId.equals(registration.providerId())
                || current.generation != registration.generation() || !current.token.equals(registration.token())
                || !ownerIdentity.equals(registration.ownerIdentity())
                || !ownerIdentity.equals(provenance.ownerIdentity())
                || !ownerCapability.equals(provenance.attestation())) {
            throw new SecurityException("Manual progress registration or provenance is stale/untrusted");
        }
        return current;
    }

    private void requireCapability(UUID capability) {
        if (!ownerCapability.equals(Objects.requireNonNull(capability, "owner capability"))) {
            throw new SecurityException("Manual progress owner capability is invalid");
        }
    }

    private MetricValue update(
            ProgressKey key,
            ManualCounterDefinition definition,
            MetricValue value,
            ProgressProvenance provenance) {
        ManualProgressRecord previous = values.get(key);
        if (previous == null && values.size() >= maximumEntries) {
            throw new IllegalStateException("Manual progress entry limit reached; update rejected for backpressure");
        }
        long version = previous == null ? 1 : Math.addExact(previous.updateVersion(), 1);
        ManualProgressRecord replacement = new ManualProgressRecord(providerId, definition.metricId(), key.playerId,
                value, version, provenance.source(), provenance.observedAt());
        values.put(key, replacement);
        dirty.put(key, replacement);
        return value;
    }

    private MetricDescriptor descriptor(ManualCounterDefinition definition) {
        return new MetricDescriptor(providerId, definition.metricId(), definition.valueType(),
                MetricOperator.compatibleWith(definition.valueType()),
                java.util.Set.of(MetricReadMode.CURRENT, MetricReadMode.LIFETIME), true,
                definition.exactSetAllowed() ? MetricMonotonicity.NON_MONOTONIC : MetricMonotonicity.MONOTONIC,
                definition.exactSetAllowed() ? MetricResetPolicy.NOT_APPLICABLE
                        : MetricResetPolicy.FAIL_RECONCILIATION,
                Map.of(), "manual." + definition.metricId().value() + ".display_name",
                "manual." + definition.metricId().value() + ".description", "none", "owner_authenticated");
    }

    private MetricValue zero(ManualCounterDefinition definition) {
        return switch (definition.valueType()) {
            case INTEGER -> MetricValue.integer(0);
            case EXACT_DECIMAL -> MetricValue.decimal("0");
            case DURATION -> MetricValue.duration(java.time.Duration.ZERO);
            case COUNT -> ZERO_COUNT;
            case CURRENCY_AMOUNT -> MetricValue.parse(definition.valueType(), "0");
            default -> throw new IllegalStateException("Non-numeric exact-set metric has no implicit default");
        };
    }

    private static void requireType(ManualCounterDefinition definition, MetricValue value) {
        Objects.requireNonNull(value, "metric value");
        if (definition.valueType() != value.type()) {
            throw new IllegalArgumentException("Manual progress value type does not match registered metric");
        }
    }

    private record RegisteredMetric(ManualCounterDefinition definition, long generation, UUID token) {
    }

    private record ProgressKey(MetricId metricId, UUID playerId) {
    }
}
