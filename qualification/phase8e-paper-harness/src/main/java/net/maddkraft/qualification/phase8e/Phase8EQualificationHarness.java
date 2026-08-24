package net.maddkraft.qualification.phase8e;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.service.MaddPrestigeService;
import net.maddkraft.maddprestige.api.service.OperationEvaluationStatus;
import net.maddkraft.maddprestige.api.service.ServiceResult;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.EventException;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Coordinates disposable Phase 8E qualification exclusively through public Paper/Stable boundaries. */
public final class Phase8EQualificationHarness extends JavaPlugin {
    private static final String ALPHA = "phase8e_alpha:alpha";
    private static final String BETA = "phase8e_beta:beta";
    private static final String MANUAL = "phase5_events";
    private static final String MANUAL_METRIC = "mcmmo_adjusted_xp_total";
    private static final String PLACEHOLDER = "%maddprestige_stage%";
    private static final int EVENT_PLAYERS = 4_096;
    private static final int MEASUREMENT_TICKS = 1_800;
    private static final int MEASUREMENT_EVENTS = 102_400;
    private static final int SERVICE_CALLS = 5_760;
    private static final int REFRESH_REQUESTS = 11_520;
    private static final int RENDER_CALLS = 180_000;
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern REVISION_PATTERN = Pattern.compile("r_[0-9a-f]{32}");

    private final ArrayDeque<Runnable> steps = new ArrayDeque<>();
    private final CopyOnWriteArrayList<String> messages = new CopyOnWriteArrayList<>();
    private final AtomicLong acceptedManual = new AtomicLong();
    private final AtomicInteger serviceStarted = new AtomicInteger();
    private final AtomicInteger serviceTerminal = new AtomicInteger();
    private final AtomicInteger serviceFailed = new AtomicInteger();
    private final AtomicInteger refreshRequests = new AtomicInteger();
    private final AtomicInteger renderCalls = new AtomicInteger();
    private final AtomicInteger renderMismatches = new AtomicInteger();
    private final AtomicInteger samples = new AtomicInteger();
    private final AtomicLong serviceLatencyNanos = new AtomicLong();
    private final AtomicLong serviceLatencyMinimumNanos = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong serviceLatencyMaximumNanos = new AtomicLong();
    private final Set<UUID> refreshSignaled = ConcurrentHashMap.newKeySet();
    private final Set<UUID> refreshMaterialized = ConcurrentHashMap.newKeySet();
    private final List<Player> eventPlayers = new ArrayList<>();
    private final List<Player> refreshPlayers = new ArrayList<>();
    private final List<UUID> servicePlayers = new ArrayList<>();
    private final List<Double> baselineMspt = new CopyOnWriteArrayList<>();
    private final List<Double> loadMspt = new CopyOnWriteArrayList<>();
    private final List<Double> recoveryMspt = new CopyOnWriteArrayList<>();
    private CommandSender sender;
    private MaddPrestigeService service;
    private java.io.File marker;
    private Connection observer;
    private Connection writeReservation;
    private String revision;
    private UUID setupSession;
    private BukkitTask sampler;
    private int passed;
    private boolean stopping;
    private long initialDataVersion;
    private long recoveryStartedNanos;
    private VirtualThreadObservation virtualThreads;
    private Phase8EFaultQualification faultQualification;

    @Override
    public void onEnable() {
        if (!"performance".equals(mode())) {
            faultQualification = new Phase8EFaultQualification(this, mode());
            faultQualification.enable();
            return;
        }
        marker = new java.io.File(getDataFolder(), "performance.properties");
        getDataFolder().mkdirs();
        virtualThreads = new VirtualThreadObservation();
        sender = commandSender();
        preparePlayers();
        getServer().getScheduler().runTaskLater(this, this::begin, 60L);
    }

    @Override
    public void onDisable() {
        if (faultQualification != null) {
            faultQualification.disable();
            return;
        }
        if (sampler != null) sampler.cancel();
        releaseWriteReservation();
        closeObserver();
        closeVirtualThreadObservation();
        getLogger().info("PHASE8E-Q shutdown mode=" + mode() + " pass-count=" + passed
                + " accepted-manual=" + acceptedManual.get());
    }

    private void begin() {
        try {
            if (marker.isFile() && "first-boot".equals(loadMarker().getProperty("completed"))) {
                buildPerformanceRestart(loadMarker());
            } else {
                buildPerformanceFresh();
            }
            advance();
        } catch (Throwable failure) {
            fail("begin", failure);
        }
    }

    private String mode() {
        return System.getProperty("phase8e.mode", "performance").toLowerCase(Locale.ROOT);
    }

    private void buildPerformanceFresh() {
        steps.add(() -> eventually("production/provider discovery", () -> {
            service = getServer().getServicesManager().load(MaddPrestigeService.class);
            return service != null && providerVisible(ALPHA) && providerVisible(BETA)
                    && pluginEnabled("mcMMO") && pluginEnabled("PlaceholderAPI");
        }, () -> {
            pass("production service, exact dependencies and two independent late providers discovered");
            advance();
        }));
        steps.add(() -> command("maddprestige setup start", lines -> {
            setupSession = UUID.fromString(extract(UUID_PATTERN, lines));
            pass("performance canonical setup session started");
            advance();
        }));
        steps.add(commandStep(() -> "maddprestige setup provider " + setupSession + " internal",
                "internal rank authority selected"));
        steps.add(commandStep(() -> "maddprestige setup stage " + setupSession + " base Base",
                "base stage added"));
        steps.add(commandStep(() -> "maddprestige setup stage " + setupSession + " alpha Alpha",
                "Alpha-qualified intermediate stage added"));
        steps.add(commandStep(() -> "maddprestige setup stage " + setupSession + " target Target",
                "target stage added"));
        steps.add(commandStep(() -> "maddprestige setup baseline " + setupSession + " base",
                "base stage selected"));
        steps.add(commandStep(() -> "maddprestige setup requirement " + setupSession
                + " alpha alpha_points " + ALPHA + " points GREATER_OR_EQUAL 1 ABSOLUTE LIVE",
                "Alpha requirement attached"));
        steps.add(commandStep(() -> "maddprestige setup requirement " + setupSession
                + " target beta_tokens " + BETA + " tokens GREATER_OR_EQUAL 1 ABSOLUTE LIVE",
                "Beta requirement attached"));
        steps.add(commandStep(() -> "maddprestige setup prestige " + setupSession + " disabled",
                "Prestige disabled for performance profile"));
        steps.add(commandStep(() -> "maddprestige setup preview " + setupSession,
                "two-provider setup preview compiled"));
        steps.add(() -> command("maddprestige setup acknowledge " + setupSession, lines -> {
            setupSession = UUID.fromString(extract(UUID_PATTERN, lines));
            pass("setup risk acknowledged with server-issued token");
            advance();
        }));
        steps.add(() -> command("maddprestige setup confirm " + setupSession + " phase8e performance profile",
                lines -> {
                    revision = extract(REVISION_PATTERN, lines);
                    pass("performance profile applied as " + revision);
                    advance();
                }));
        steps.add(() -> enablePerformanceIntegrations(this::advance));
        steps.add(() -> eventually("active multi-provider and event composition", () ->
                providerVisible(ALPHA) && providerVisible(BETA) && providerVisible("mcmmo")
                        && providerVisible(MANUAL), () -> await("initial two-provider evaluation",
                service.evaluateRankUp(servicePlayers.getFirst()), result -> {
                    require(result.successful() && result.value().orElseThrow().status()
                            == OperationEvaluationStatus.ELIGIBLE,
                            "two-provider performance profile is not eligible");
                    openObserver();
                    initialDataVersion = dataVersion();
                    pass("active revision contains distinct Alpha and Beta stage requirements with production "
                            + "mcMMO/manual capabilities");
                    advance();
                })));
        steps.add(this::preconditionAlternatingProviderTargets);
        steps.add(() -> measureWindow("control", 600, () -> {
            pass("30-second same-process control window recorded");
            advance();
        }));
        steps.add(() -> measureWindow("warmup", 400, () -> {
            pass("20-second JVM/runtime stabilization warm-up recorded");
            advance();
        }));
        steps.add(this::runMeasurementLoad);
        steps.add(this::awaitMeasurementConvergence);
        steps.add(this::exercisePersistenceStall);
        steps.add(this::prepareDirtyShutdown);
    }

    private void buildPerformanceRestart(Properties properties) {
        long expected = Long.parseLong(required(properties, "acceptedManual"));
        int expectedRows = Integer.parseInt(required(properties, "manualRows"));
        revision = required(properties, "revision");
        acceptedManual.set(expected);
        steps.add(() -> eventually("unchanged restart composition", () -> {
            service = getServer().getServicesManager().load(MaddPrestigeService.class);
            return service != null && providerVisible(ALPHA) && providerVisible(BETA)
                    && providerVisible("mcmmo") && providerVisible(MANUAL);
        }, () -> {
            openObserver();
            ManualSnapshot snapshot = manualSnapshot();
            require(snapshot.sum().compareTo(BigDecimal.valueOf(expected)) == 0,
                    "restart manual sum differs: " + snapshot);
            require(snapshot.rows() == expectedRows, "restart manual row count differs: " + snapshot);
            require(manualHealth() == ProviderHealthState.ACTIVE
                            || manualHealth() == ProviderHealthState.AVAILABLE,
                    "manual provider did not restart healthy");
            pass("unchanged restart recovered exact accepted manual total, rows and healthy provider");
            signalRefresh(refreshPlayers.getFirst());
            eventually("restart Placeholder materialization", () ->
                    "base".equals(render(refreshPlayers.getFirst().getUniqueId())), () ->
                    await("restart two-provider evaluation", service.evaluateRankUp(servicePlayers.get(1)), result -> {
                        require(result.successful() && result.value().orElseThrow().status()
                                == OperationEvaluationStatus.ELIGIBLE,
                                "restart multi-provider evaluation is not eligible");
                        pass("restart preserved cache publication and two-provider public evaluation");
                        advance();
                    }));
        }));
        steps.add(() -> {
            pass("performance qualification completed with bounded normal second shutdown");
            finishServer("PHASE8E-PERF COMPLETE two-boot pass-count=" + passed + " samples=" + samples.get()
                    + " accepted-manual=" + acceptedManual.get());
        });
    }

    private void enablePerformanceIntegrations(Runnable success) {
        command("maddprestige config draft", lines -> {
            String draft = extract(UUID_PATTERN, lines);
            command("maddprestige config set " + draft + " integrations.mcmmo.enabled true", ignored ->
                    command("maddprestige config set " + draft
                                    + " integrations.placeholderapi.output.enabled true", ignored2 ->
                            command("maddprestige config validate " + draft, validated ->
                                    command("maddprestige config apply " + draft + " " + revision
                                            + " phase8e exact performance integrations", applied -> {
                                        revision = extract(REVISION_PATTERN, applied);
                                        pass("mcMMO and Placeholder output enabled by canonical revision "
                                                + revision);
                                        success.run();
                                    }))));
        });
    }

    private void runMeasurementLoad() {
        AtomicInteger tick = new AtomicInteger();
        AtomicInteger events = new AtomicInteger();
        AtomicInteger services = new AtomicInteger();
        AtomicInteger refreshes = new AtomicInteger();
        AtomicInteger renders = new AtomicInteger();
        startSampler("load");
        final BukkitTask[] task = new BukkitTask[1];
        task[0] = getServer().getScheduler().runTaskTimer(this, () -> {
            try {
                int currentTick = tick.getAndIncrement();
                distribute(MEASUREMENT_EVENTS, MEASUREMENT_TICKS, currentTick, events, index ->
                        deliverXp(eventPlayers.get(index % EVENT_PLAYERS)));
                distribute(SERVICE_CALLS, MEASUREMENT_TICKS, currentTick, services, index ->
                        evaluate(servicePlayers.get(index % servicePlayers.size())));
                distribute(REFRESH_REQUESTS, MEASUREMENT_TICKS, currentTick, refreshes, index ->
                        signalRefresh(refreshPlayers.get(index % refreshPlayers.size())));
                distribute(RENDER_CALLS, MEASUREMENT_TICKS, currentTick, renders, index -> {
                    UUID playerId = refreshPlayers.get(index % refreshPlayers.size()).getUniqueId();
                    String value = render(playerId);
                    renderCalls.incrementAndGet();
                    if (!"base".equals(value)) renderMismatches.incrementAndGet();
                });
                if (currentTick + 1 >= MEASUREMENT_TICKS) {
                    task[0].cancel();
                    stopSampler();
                    require(events.get() == MEASUREMENT_EVENTS, "measurement event distribution changed");
                    require(services.get() == SERVICE_CALLS, "measurement service distribution changed");
                    require(refreshes.get() == REFRESH_REQUESTS, "measurement refresh distribution changed");
                    require(renders.get() == RENDER_CALLS, "measurement render distribution changed");
                    pass("fixed 90-second measurement workload generated exact declared counts");
                    advance();
                }
            } catch (Throwable failure) {
                task[0].cancel();
                stopSampler();
                fail("measurement load", failure);
            }
        }, 1L, 1L);
    }

    private void preconditionAlternatingProviderTargets() {
        AtomicInteger terminal = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        for (int index = 1; index < servicePlayers.size(); index += 2) {
            service.rankUp(servicePlayers.get(index)).whenComplete((result, failure) -> {
                if (failure != null || result == null || !result.successful()
                        || result.value().orElseThrow().status()
                        != net.maddkraft.maddprestige.api.service.OperationStatus.COMPLETED) {
                    failed.incrementAndGet();
                }
                terminal.incrementAndGet();
            });
        }
        eventually("alternating provider target preconditioning", () -> terminal.get() == servicePlayers.size() / 2,
                () -> {
                    require(failed.get() == 0, "provider target preconditioning failed " + failed.get() + " times");
                    pass("128 players advanced through Alpha while 128 remained at the Alpha-qualified target; "
                            + "load evaluations now alternate Alpha and Beta");
                    advance();
                });
    }

    private void awaitMeasurementConvergence() {
        recoveryStartedNanos = System.nanoTime();
        AtomicInteger consecutive = new AtomicInteger();
        startSampler("recovery");
        eventually("measurement service/manual/cache convergence", Duration.ofSeconds(30), () -> {
            ManualSnapshot snapshot = manualSnapshot();
            VirtualThreadObservation.Snapshot virtual = virtualThreads.snapshot();
            boolean converged = serviceTerminal.get() == SERVICE_CALLS
                    && serviceFailed.get() == 0
                    && snapshot.rows() == EVENT_PLAYERS
                    && snapshot.sum().compareTo(BigDecimal.valueOf(MEASUREMENT_EVENTS)) == 0
                    && refreshPlayers.stream().allMatch(player ->
                            "base".equals(render(player.getUniqueId())))
                    && virtual.converged();
            if (!converged) {
                consecutive.set(0);
                return false;
            }
            return consecutive.incrementAndGet() >= 2;
        }, () -> {
            stopSampler();
            long convergenceMillis = Duration.ofNanos(System.nanoTime() - recoveryStartedNanos).toMillis();
            require(renderMismatches.get() < RENDER_CALLS,
                    "every cache render missed; output was never materialized");
            require(serviceStarted.get() == SERVICE_CALLS && serviceTerminal.get() == SERVICE_CALLS,
                    "service work did not converge exactly");
            require(dataVersion() > initialDataVersion, "SQLite observer saw no production commit movement");
            VirtualThreadObservation.Snapshot virtual = virtualThreads.snapshot();
            require(virtual.operation().started() > 0 && virtual.operation().highWater() > 0,
                    "JFR observed no production operation virtual threads");
            require(virtual.placeholder().started() > 0 && virtual.placeholder().highWater() > 0,
                    "JFR observed no production Placeholder virtual threads");
            require(virtual.submitFailures() == 0, "JFR observed a virtual-thread submit failure");
            pass("manual rows/sum, public service stages, Placeholder publication and JFR task families "
                    + "converged exactly in " + convergenceMillis + " ms; " + virtual);
            advance();
        });
    }

    private void exercisePersistenceStall() {
        acquireWriteReservation();
        for (int index = 0; index < 2_048; index++) {
            deliverXp(eventPlayers.get(index % 256));
        }
        eventually("manual persistence degraded health", Duration.ofSeconds(20), () ->
                manualHealth() == ProviderHealthState.DEGRADED, () -> {
            pass("qualification-held SQLite reservation produced public degraded manual health without Paper stall");
            releaseWriteReservation();
            eventually("manual persistence recovery", Duration.ofSeconds(20), () -> {
                ManualSnapshot snapshot = manualSnapshot();
                return (manualHealth() == ProviderHealthState.ACTIVE
                        || manualHealth() == ProviderHealthState.AVAILABLE)
                        && snapshot.sum().compareTo(BigDecimal.valueOf(acceptedManual.get())) == 0;
            }, () -> {
                pass("released reservation retried, recovered health and converged without lost/duplicate progress");
                advance();
            });
        });
    }

    private void prepareDirtyShutdown() {
        for (int index = 0; index < 512; index++) {
            deliverXp(eventPlayers.get(index % 256));
        }
        ManualSnapshot before = manualSnapshot();
        require(before.sum().compareTo(BigDecimal.valueOf(acceptedManual.get())) < 0,
                "dirty shutdown segment unexpectedly flushed before sealing the dirty-state observation");
        Properties properties = new Properties();
        properties.setProperty("completed", "first-boot");
        properties.setProperty("revision", revision);
        properties.setProperty("acceptedManual", Long.toString(acceptedManual.get()));
        properties.setProperty("manualRows", Integer.toString(EVENT_PLAYERS));
        try (OutputStream output = java.nio.file.Files.newOutputStream(marker.toPath())) {
            properties.store(output, "Phase 8E performance first-boot exact state");
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        pass("dirty shutdown state sealed before periodic flush");
        finishServer("PHASE8E-PERF FIRST-BOOT DIRTY-SHUTDOWN accepted-manual=" + acceptedManual.get()
                + " durable-before=" + before.sum().toPlainString() + " pass-count=" + passed);
    }

    private void deliverXp(Player player) {
        try {
            Class<?> skillType = Class.forName("com.gmail.nossr50.datatypes.skills.PrimarySkillType");
            Class<?> reasonType = Class.forName("com.gmail.nossr50.datatypes.experience.XPGainReason");
            Class<?> eventType = Class.forName("com.gmail.nossr50.events.experience.McMMOPlayerXpGainEvent");
            Object mining = enumConstant(skillType, "MINING");
            Object reason = enumConstant(reasonType, "UNKNOWN");
            org.bukkit.event.Event event = (org.bukkit.event.Event) eventType
                    .getConstructor(Player.class, skillType, float.class, reasonType)
                    .newInstance(player, mining, 1.0F, reason);
            getServer().getPluginManager().callEvent(event);
            require(!((org.bukkit.event.Cancellable) event).isCancelled(),
                    "qualification XP event was unexpectedly cancelled");
            acceptedManual.incrementAndGet();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot invoke the exact mcMMO XP event surface", failure);
        }
    }

    private void evaluate(UUID playerId) {
        serviceStarted.incrementAndGet();
        long started = System.nanoTime();
        try {
            CompletionStage<ServiceResult<net.maddkraft.maddprestige.api.service.OperationEvaluation>> stage =
                    service.evaluateRankUp(playerId);
            require(stage != null, "public evaluation returned a null stage");
            stage.whenComplete((result, failure) -> {
                observeServiceLatency(System.nanoTime() - started);
                if (failure != null || result == null || !result.successful()
                        || result.value().orElseThrow().status() != OperationEvaluationStatus.ELIGIBLE) {
                    serviceFailed.incrementAndGet();
                }
                serviceTerminal.incrementAndGet();
            });
        } catch (RuntimeException failure) {
            observeServiceLatency(System.nanoTime() - started);
            serviceFailed.incrementAndGet();
            serviceTerminal.incrementAndGet();
        }
    }

    private void signalRefresh(Player player) {
        refreshRequests.incrementAndGet();
        refreshSignaled.add(player.getUniqueId());
        PlayerJoinEvent event = new PlayerJoinEvent(player, Component.empty());
        int delivered = 0;
        for (RegisteredListener listener : event.getHandlers().getRegisteredListeners()) {
            if ("MaddPrestige".equals(listener.getPlugin().getName())) {
                try {
                    listener.callEvent(event);
                } catch (EventException failure) {
                    throw new IllegalStateException("MaddPrestige PlayerJoinEvent refresh listener failed", failure);
                }
                delivered++;
            }
        }
        require(delivered > 0, "MaddPrestige did not register a PlayerJoinEvent refresh listener");
    }

    private String render(UUID playerId) {
        try {
            Class<?> placeholderApi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            String value = (String) placeholderApi.getMethod("setPlaceholders",
                    org.bukkit.OfflinePlayer.class, String.class)
                    .invoke(null, getServer().getOfflinePlayer(playerId), PLACEHOLDER);
            if ("base".equals(value)) refreshMaterialized.add(playerId);
            return value;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot invoke the exact PlaceholderAPI render surface", failure);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumConstant(Class<?> type, String name) {
        return Enum.valueOf((Class<? extends Enum>) type.asSubclass(Enum.class), name);
    }

    private void measureWindow(String window, long ticks, Runnable continuation) {
        startSampler(window);
        getServer().getScheduler().runTaskLater(this, () -> {
            stopSampler();
            continuation.run();
        }, ticks);
    }

    private void startSampler(String window) {
        stopSampler();
        sampler = getServer().getScheduler().runTaskTimer(this, () -> sample(window), 1L, 20L);
    }

    private void stopSampler() {
        if (sampler != null) {
            sampler.cancel();
            sampler = null;
        }
    }

    private void sample(String window) {
        double[] tps = getServer().getTPS();
        double mspt = getServer().getAverageTickTime();
        switch (window) {
            case "control" -> baselineMspt.add(mspt);
            case "load" -> loadMspt.add(mspt);
            case "recovery" -> recoveryMspt.add(mspt);
            default -> { }
        }
        Set<Thread> platformSnapshot = Thread.getAllStackTraces().keySet();
        long platformThreads = platformSnapshot.stream().filter(Thread::isAlive).count();
        long mpPlatformThreads = platformSnapshot.stream().filter(Thread::isAlive)
                .map(Thread::getName).filter(name -> name.startsWith("maddprestige-")).count();
        long providerPlatformThreads = platformSnapshot.stream().filter(Thread::isAlive)
                .map(Thread::getName).filter(name -> name.startsWith("maddprestige-provider-")).count();
        java.lang.management.MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        java.lang.management.MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        ManualSnapshot manual = observer == null ? new ManualSnapshot(0, BigDecimal.ZERO) : manualSnapshot();
        long manualBacklog = acceptedManual.get() - manual.sum().longValueExact();
        int serviceBacklog = serviceStarted.get() - serviceTerminal.get();
        int placeholderBacklog = refreshSignaled.size() - refreshMaterialized.size();
        VirtualThreadObservation.Snapshot virtual = virtualThreads.snapshot();
        int sequence = samples.incrementAndGet();
        getLogger().info("PHASE8E-PERF SAMPLE n=" + sequence + " window=" + window
                + " tps1=" + decimal(tps[0]) + " tps5=" + decimal(tps[1]) + " tps15=" + decimal(tps[2])
                + " mspt=" + decimal(mspt) + " pendingTasks=" + getServer().getScheduler().getPendingTasks().size()
                + " platformThreads=" + platformThreads + " mpPlatformThreads=" + mpPlatformThreads
                + " providerPlatformThreads=" + providerPlatformThreads + " heapUsed=" + heap.getUsed()
                + " heapCommitted=" + heap.getCommitted() + " heapMax=" + heap.getMax()
                + " nonHeapUsed=" + nonHeap.getUsed() + " nonHeapCommitted=" + nonHeap.getCommitted()
                + " nonHeapMax=" + nonHeap.getMax() + " manualAccepted=" + acceptedManual.get()
                + " manualDurable=" + manual.sum().toPlainString() + " manualRows=" + manual.rows()
                + " manualBacklog=" + manualBacklog + " dataVersion="
                + (observer == null ? -1L : dataVersion())
                + " serviceStarted=" + serviceStarted.get() + " serviceTerminal=" + serviceTerminal.get()
                + " serviceFailed=" + serviceFailed.get() + " serviceBacklog=" + serviceBacklog
                + " serviceLatencyCount=" + serviceTerminal.get()
                + " serviceLatencyMinMicros=" + nanosToMicros(serviceLatencyMinimumNanos.get())
                + " serviceLatencyMeanMicros=" + nanosToMicros(serviceTerminal.get() == 0 ? 0L
                        : serviceLatencyNanos.get() / serviceTerminal.get())
                + " serviceLatencyMaxMicros=" + nanosToMicros(serviceLatencyMaximumNanos.get())
                + " refreshRequests=" + refreshRequests.get()
                + " refreshDistinct=" + refreshSignaled.size()
                + " materializedDistinct=" + refreshMaterialized.size()
                + " placeholderBacklog=" + placeholderBacklog
                + " renderCalls=" + renderCalls.get() + " renderMismatches=" + renderMismatches.get()
                + " manualHealth=" + (service == null ? "UNKNOWN" : manualHealth())
                + virtual.logFields());
    }

    private void observeServiceLatency(long nanos) {
        serviceLatencyNanos.addAndGet(nanos);
        serviceLatencyMinimumNanos.accumulateAndGet(nanos, Math::min);
        serviceLatencyMaximumNanos.accumulateAndGet(nanos, Math::max);
    }

    private static long nanosToMicros(long nanos) {
        return nanos == Long.MAX_VALUE ? 0L : Duration.ofNanos(nanos).toNanos() / 1_000L;
    }

    private void closeVirtualThreadObservation() {
        if (virtualThreads == null) return;
        virtualThreads.close();
        virtualThreads = null;
    }

    private void openObserver() {
        if (observer != null) return;
        try {
            observer = sqliteConnection();
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot open disposable SQLite observer", exception);
        }
    }

    private void closeObserver() {
        if (observer != null) {
            try {
                observer.close();
            } catch (Exception ignored) {
                // Disposable read-only observer cleanup cannot affect production state.
            }
            observer = null;
        }
    }

    private ManualSnapshot manualSnapshot() {
        try (Statement statement = observer.createStatement(); ResultSet rows = statement.executeQuery(
                "SELECT value_text FROM mp_manual_progress WHERE provider_id='" + MANUAL
                        + "' AND metric_id='" + MANUAL_METRIC + "'")) {
            int count = 0;
            BigDecimal sum = BigDecimal.ZERO;
            while (rows.next()) {
                count++;
                sum = sum.add(new BigDecimal(rows.getString(1)));
            }
            return new ManualSnapshot(count, sum);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot inspect disposable manual progress", exception);
        }
    }

    private long dataVersion() {
        try (Statement statement = observer.createStatement(); ResultSet rows = statement.executeQuery(
                "PRAGMA data_version")) {
            require(rows.next(), "SQLite data_version returned no row");
            return rows.getLong(1);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot observe SQLite data version", exception);
        }
    }

    private void acquireWriteReservation() {
        require(writeReservation == null, "write reservation already held");
        try {
            writeReservation = sqliteConnection();
            try (Statement statement = writeReservation.createStatement()) {
                statement.execute("BEGIN IMMEDIATE");
            }
            getLogger().info("PHASE8E-PERF controlled SQLite write reservation acquired without data mutation");
        } catch (Exception exception) {
            releaseWriteReservation();
            throw new IllegalStateException("Cannot acquire controlled SQLite write reservation", exception);
        }
    }

    private void releaseWriteReservation() {
        if (writeReservation == null) return;
        try (Statement statement = writeReservation.createStatement()) {
            statement.execute("ROLLBACK");
        } catch (Exception ignored) {
            // Closing the disposable connection below also releases the transaction reservation.
        }
        try {
            writeReservation.close();
        } catch (Exception ignored) {
            // Best-effort close of qualification-owned connection.
        }
        writeReservation = null;
        getLogger().info("PHASE8E-PERF controlled SQLite write reservation released");
    }

    private Connection sqliteConnection() throws Exception {
        org.bukkit.plugin.Plugin production = getServer().getPluginManager().getPlugin("MaddPrestige");
        require(production != null, "production plugin is absent");
        Class<?> type = Class.forName("org.sqlite.JDBC", true, production.getClass().getClassLoader());
        Driver driver = (Driver) type.getConstructor().newInstance();
        Path database = production.getDataFolder().toPath().resolve("maddprestige-v2.sqlite");
        Connection connection = driver.connect("jdbc:sqlite:" + database.toAbsolutePath(), new Properties());
        if (connection == null) throw new IllegalStateException("SQLite driver rejected disposable database URL");
        return connection;
    }

    private ProviderHealthState manualHealth() {
        if (service == null) return ProviderHealthState.UNAVAILABLE;
        return service.providers().value().orElse(List.of()).stream()
                .filter(provider -> provider.id().value().equals(MANUAL)).findFirst()
                .map(net.maddkraft.maddprestige.api.service.ProviderView::health)
                .orElse(ProviderHealthState.UNAVAILABLE);
    }

    private boolean providerVisible(String id) {
        if (service == null) return false;
        var result = service.providers();
        return result.successful() && result.value().orElseThrow().stream()
                .anyMatch(provider -> provider.id().value().equals(id));
    }

    private boolean pluginEnabled(String name) {
        org.bukkit.plugin.Plugin plugin = getServer().getPluginManager().getPlugin(name);
        return plugin != null && plugin.isEnabled();
    }

    private void preparePlayers() {
        for (int index = 0; index < EVENT_PLAYERS; index++) {
            UUID id = new UUID(0x8e00000000000000L, index + 1L);
            eventPlayers.add(player(id, "E" + index));
        }
        for (int index = 0; index < 128; index++) {
            UUID id = new UUID(0x8e10000000000000L, index + 1L);
            refreshPlayers.add(player(id, "R" + index));
        }
        for (int index = 0; index < 256; index++) {
            servicePlayers.add(new UUID(0x8e20000000000000L, index + 1L));
        }
    }

    private Player player(UUID id, String name) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getName", "getDisplayName", "getPlayerListName" -> name;
                    case "isOnline", "isValid", "hasPermission", "isPermissionSet", "isOp" -> true;
                    case "getServer" -> getServer();
                    case "getWorld" -> getServer().getWorlds().getFirst();
                    case "getLocation" -> new Location(getServer().getWorlds().getFirst(), 0, 64, 0);
                    case "getGameMode" -> GameMode.SURVIVAL;
                    case "spigot" -> new Player.Spigot();
                    case "toString" -> "Phase8EPlayer[" + id + "]";
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == arguments[0];
                    default -> defaultValue(method.getReturnType());
                });
    }

    private CommandSender commandSender() {
        return (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                new Class<?>[] {CommandSender.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("sendMessage") && arguments != null) {
                        for (Object argument : arguments) {
                            if (argument instanceof Component component) {
                                messages.add(PlainTextComponentSerializer.plainText().serialize(component));
                            } else if (argument instanceof String text) {
                                messages.add(text);
                            } else if (argument instanceof String[] lines) {
                                messages.addAll(List.of(lines));
                            }
                        }
                        return null;
                    }
                    return switch (method.getName()) {
                        case "hasPermission", "isPermissionSet", "isOp" -> true;
                        case "getName" -> "Phase8E-Harness";
                        case "getServer" -> getServer();
                        case "spigot" -> new CommandSender.Spigot();
                        case "toString" -> "Phase8E-Harness-CommandSender";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == arguments[0];
                        default -> defaultValue(method.getReturnType());
                    };
                });
    }

    private static Object defaultValue(Class<?> type) {
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

    private Runnable commandStep(java.util.function.Supplier<String> command, String label) {
        return () -> command(command.get(), ignored -> {
            pass(label);
            advance();
        });
    }

    private void command(String command, Consumer<List<String>> continuation) {
        messages.clear();
        String[] tokens = command.split(" +");
        require(tokens.length > 0 && tokens[0].equals("maddprestige"), "unexpected command ingress");
        var registered = getServer().getPluginCommand("maddprestige");
        require(registered != null && registered.execute(sender, "maddprestige",
                Arrays.copyOfRange(tokens, 1, tokens.length)), "command rejected: " + command);
        eventually("command response " + command, () -> !messages.isEmpty(), () -> {
            List<String> snapshot = List.copyOf(messages);
            require(snapshot.stream().noneMatch(Phase8EQualificationHarness::failureDiagnostic),
                    "command failed: " + command + " -> " + snapshot);
            continuation.accept(snapshot);
        });
    }

    private static boolean failureDiagnostic(String line) {
        String normalized = line.toLowerCase(Locale.ROOT);
        return normalized.contains("[error]") || normalized.contains("internal failure")
                || normalized.contains("command failed") || normalized.startsWith("usage:");
    }

    private <T> void await(String label, CompletionStage<T> stage, Consumer<T> continuation) {
        stage.whenComplete((value, failure) -> getServer().getScheduler().runTask(this, () -> {
            if (failure != null) {
                fail(label, failure);
                return;
            }
            try {
                continuation.accept(value);
            } catch (Throwable exception) {
                fail(label, exception);
            }
        }));
    }

    private void eventually(String label, BooleanSupplier condition, Runnable continuation) {
        eventually(label, Duration.ofSeconds(30), condition, continuation);
    }

    private void eventually(String label, Duration timeout, BooleanSupplier condition, Runnable continuation) {
        long deadline = System.nanoTime() + timeout.toNanos();
        final BukkitTask[] task = new BukkitTask[1];
        task[0] = getServer().getScheduler().runTaskTimer(this, () -> {
            try {
                if (condition.getAsBoolean()) {
                    task[0].cancel();
                    continuation.run();
                } else if (System.nanoTime() >= deadline) {
                    task[0].cancel();
                    fail(label, new IllegalStateException("timed out"));
                }
            } catch (Throwable failure) {
                task[0].cancel();
                fail(label, failure);
            }
        }, 1L, 1L);
    }

    private void advance() {
        Runnable next = steps.poll();
        if (next != null) {
            getServer().getScheduler().runTaskLater(this, () -> {
                try {
                    next.run();
                } catch (Throwable failure) {
                    fail("qualification step", failure);
                }
            }, 1L);
        }
    }

    private void pass(String message) {
        passed++;
        getLogger().info("PHASE8E-Q PASS " + passed + " " + message);
    }

    private void fail(String label, Throwable failure) {
        if (stopping) return;
        stopping = true;
        getLogger().log(java.util.logging.Level.SEVERE, "PHASE8E-Q FAIL " + label, failure);
        finishServer("PHASE8E-Q FAILED mode=" + mode() + " pass-count=" + passed);
    }

    private void finishServer(String message) {
        stopping = true;
        getLogger().info(message);
        getServer().getScheduler().runTaskLater(this, getServer()::shutdown, 20L);
    }

    private Properties loadMarker() {
        Properties properties = new Properties();
        try (InputStream input = java.nio.file.Files.newInputStream(marker.toPath())) {
            properties.load(input);
            return properties;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("marker missing " + key);
        return value;
    }

    private static String extract(Pattern pattern, List<String> lines) {
        Matcher matcher = pattern.matcher(String.join("\n", lines));
        if (!matcher.find()) throw new IllegalStateException("expected identity absent from " + lines);
        return matcher.group();
    }

    private static void distribute(
            int total,
            int ticks,
            int tick,
            AtomicInteger counter,
            java.util.function.IntConsumer action) {
        int through = (int) (((long) total * (tick + 1)) / ticks);
        while (counter.get() < through) {
            int index = counter.getAndIncrement();
            action.accept(index);
        }
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private record ManualSnapshot(int rows, BigDecimal sum) {
    }
}
