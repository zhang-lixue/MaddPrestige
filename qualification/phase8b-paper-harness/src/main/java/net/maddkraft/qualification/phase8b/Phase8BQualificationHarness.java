package net.maddkraft.qualification.phase8b;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.maddkraft.maddprestige.api.event.OperationEventSnapshot;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.metric.MetricMonotonicity;
import net.maddkraft.maddprestige.api.metric.MetricOperator;
import net.maddkraft.maddprestige.api.metric.MetricProvider;
import net.maddkraft.maddprestige.api.metric.MetricQuery;
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
import net.maddkraft.maddprestige.api.provider.ProviderSnapshot;
import net.maddkraft.maddprestige.api.provider.RequirementProvider;
import net.maddkraft.maddprestige.api.service.MaddPrestigeService;
import net.maddkraft.maddprestige.api.service.OperationEvaluationStatus;
import net.maddkraft.maddprestige.api.service.OperationResult;
import net.maddkraft.maddprestige.api.service.OperationStatus;
import net.maddkraft.maddprestige.api.service.PlayerProgressSnapshot;
import net.maddkraft.maddprestige.api.service.ServiceResult;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Runs the two-boot Phase 8B qualification against the real production plugin composition. */
public final class Phase8BQualificationHarness extends JavaPlugin {
    private static final String EXTERNAL_ID = "phase8b_harness:external";
    private static final String PLACEHOLDER = "%maddprestige_stage%";
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern REVISION_PATTERN = Pattern.compile("r_[0-9a-f]{32}");

    private final CopyOnWriteArrayList<String> messages = new CopyOnWriteArrayList<>();
    private final ArrayDeque<Runnable> steps = new ArrayDeque<>();
    private final AtomicInteger providerReads = new AtomicInteger();
    private final AtomicInteger providerQueryCount = new AtomicInteger();
    private final Set<UUID> postRankPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<UUID> postPrestigePlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<UUID, OperationEventSnapshot> preRankEvents = new ConcurrentHashMap<>();
    private final Map<UUID, OperationEventSnapshot> postRankEvents = new ConcurrentHashMap<>();
    private final Map<UUID, OperationEventSnapshot> prePrestigeEvents = new ConcurrentHashMap<>();
    private final Map<UUID, OperationEventSnapshot> postPrestigeEvents = new ConcurrentHashMap<>();
    private CommandSender sender;
    private MaddPrestigeService service;
    private QualificationProvider declaration;
    private volatile ProviderRegistrationHandle handle;
    private volatile UUID cancelledPlayer;
    private volatile UUID stalePrePlayer;
    private volatile boolean omitOneMultiKeyResult;
    private UUID setupSession;
    private UUID progressedPlayer;
    private String revision;
    private java.io.File marker;
    private int passed;
    private boolean stopping;

    @Override
    public void onEnable() {
        marker = new java.io.File(getDataFolder(), "qualification.properties");
        getDataFolder().mkdirs();
        sender = commandSender();
        registerLifecycleEvents();
        registerExternalProvider();
        getServer().getScheduler().runTaskLater(this, this::begin, 40L);
    }

    @Override
    public void onDisable() {
        getLogger().info("PHASE8B-Q shutdown pass-count=" + passed);
    }

    private void begin() {
        try {
            if (marker.isFile() && loadMarker().getProperty("completed") != null) {
                buildRestartSteps(loadMarker());
            } else {
                buildFreshSteps();
            }
            advance();
        } catch (Throwable failure) {
            fail("begin", failure);
        }
    }

    private void buildFreshSteps() {
        steps.add(() -> eventually("service/provider discovery", () -> {
            service = getServer().getServicesManager().load(MaddPrestigeService.class);
            return service != null && handle != null && providerVisible(EXTERNAL_ID) && providerVisible("luckperms");
        }, () -> {
            require(service.stages().successful() && service.stages().value().orElseThrow().isEmpty(),
                    "fresh stage catalog must be dormant");
            await("dormant evaluation", service.evaluateRankUp(UUID.randomUUID()), result -> {
                require(result.successful(), "dormant evaluation must be a structured result");
                require(result.value().orElseThrow().status() == OperationEvaluationStatus.UNAVAILABLE,
                        "fresh evaluation must be unavailable");
                UUID blockedPlayer = UUID.randomUUID();
                await("dormant operation", service.rankUp(blockedPlayer), blocked -> {
                    require(blocked.successful(), "dormant operation must return a structured result");
                    OperationResult operation = blocked.value().orElseThrow();
                    require(operation.requestId() != null && operation.status() == OperationStatus.BLOCKED
                            && operation.durableOperationId().isEmpty(),
                            "authorization-blocked operation lost its request identity or claimed durability");
                    require(!preRankEvents.containsKey(blockedPlayer),
                            "authorization-blocked operation delivered PRE without authorization");
                    pass("fresh service is discoverable, dormant, correlated, and provider bridge is live");
                    advance();
                });
            });
        }));
        steps.add(() -> command("maddprestige setup discover", lines -> {
            require(text(lines).contains("Active V2 configuration present: false"),
                    "setup discovery did not report dormant state");
            pass("canonical setup discovery is available while dormant");
            advance();
        }));
        steps.add(() -> command("maddprestige setup start", lines -> {
            setupSession = UUID.fromString(extract(UUID_PATTERN, lines));
            pass("canonical setup session started");
            advance();
        }));
        steps.add(commandStep(() -> "maddprestige setup provider " + setupSession + " internal",
                "internal rank provider selected"));
        steps.add(commandStep(() -> "maddprestige setup stage " + setupSession + " novice Novice",
                "baseline stage added"));
        steps.add(commandStep(() -> "maddprestige setup stage " + setupSession + " expert Expert",
                "target stage added"));
        steps.add(commandStep(() -> "maddprestige setup baseline " + setupSession + " novice",
                "baseline selected"));
        steps.add(commandStep(() -> "maddprestige setup requirement " + setupSession
                + " external_points_req " + EXTERNAL_ID + " external_points GREATER_OR_EQUAL 1 ABSOLUTE LIVE",
                "external requirement configured"));
        steps.add(commandStep(() -> "maddprestige setup prestige " + setupSession + " enabled expert novice",
                "Prestige lifecycle configured"));
        steps.add(commandStep(() -> "maddprestige setup preview " + setupSession,
                "setup preview compiled through canonical administration"));
        steps.add(() -> command("maddprestige setup acknowledge " + setupSession, lines -> {
            setupSession = UUID.fromString(extract(UUID_PATTERN, lines));
            pass("setup activation risk explicitly acknowledged through canonical administration");
            advance();
        }));
        steps.add(() -> command("maddprestige setup confirm " + setupSession + " phase8b qualification", lines -> {
            revision = extract(REVISION_PATTERN, lines);
            pass("acknowledged setup applied as exact revision " + revision);
            advance();
        }));
        steps.add(() -> eventually("active runtime", () -> service.stages().successful()
                && service.stages().value().orElseThrow().size() == 2, () -> {
            UUID candidate = UUID.randomUUID();
            await("side-effect-free evaluation", service.evaluateRankUp(candidate), evaluation -> {
                require(evaluation.successful(), "evaluation service failed");
                require(evaluation.value().orElseThrow().status() == OperationEvaluationStatus.ELIGIBLE,
                        "external metric should make rank-up eligible");
                require(evaluation.value().orElseThrow().requirements().size() >= 1,
                        "requirement progress must be public");
                require(evaluation.value().orElseThrow().simulation().isPresent(),
                        "side-effect-free simulation must be public");
                require(providerReads.get() > 0, "production evaluation did not call the external provider");
                await("currency surface", service.currencies(candidate), currencies -> {
                    require(currencies.successful() && currencies.value().orElseThrow().isEmpty(),
                            "empty configured currencies must be represented truthfully");
                    await("season surface", service.activeSeason(candidate), season -> {
                        require(season.successful() && season.value().orElseThrow().isEmpty(),
                                "absent active season must be represented truthfully");
                        pass("public evaluation/progress/currency/season surfaces use live provider state");
                        advance();
                    });
                });
            });
        }));
        steps.add(this::exerciseMultiKeyOmission);
        steps.add(this::invalidateAuthorityDuringSuccessfulPre);
        steps.add(this::cancelUnknownPlayer);
        steps.add(this::completeRankAndPrestige);
        steps.add(() -> applyPlaceholderSetting(true, () -> eventually("Placeholder enable", () ->
                "novice".equals(renderPlaceholder(progressedPlayer)), () -> {
            pass("canonical apply enabled production Placeholder output with materialized snapshot");
            advance();
        })));
        steps.add(() -> applyPlaceholderSetting(false, () -> eventually("Placeholder disable", () ->
                PLACEHOLDER.equals(renderPlaceholder(progressedPlayer)), () -> {
            pass("canonical apply disabled and unregistered Placeholder output");
            advance();
        })));
        steps.add(() -> applyPlaceholderSetting(true, () -> eventually("Placeholder re-enable", () ->
                "novice".equals(renderPlaceholder(progressedPlayer)), () -> {
            pass("canonical apply deterministically re-enabled Placeholder output");
            advance();
        })));
        steps.add(this::exerciseProviderLifecycle);
        steps.add(this::finishFreshBoot);
    }

    private void buildRestartSteps(Properties properties) {
        revision = requireProperty(properties, "revision");
        progressedPlayer = UUID.fromString(requireProperty(properties, "player"));
        steps.add(() -> eventually("restart exact composition", () -> {
            service = getServer().getServicesManager().load(MaddPrestigeService.class);
            return service != null && handle != null && service.stages().successful()
                    && service.stages().value().orElseThrow().size() == 2
                    && providerVisible(EXTERNAL_ID) && providerVisible("luckperms");
        }, () -> await("restart progress", service.playerProgress(progressedPlayer), result -> {
            require(result.successful(), "restart progress query failed");
            PlayerProgressSnapshot snapshot = result.value().orElseThrow();
            require(snapshot.stage().orElseThrow().value().equals("novice"), "restart stage was not durable");
            require(snapshot.currentPrestige() == 1 && snapshot.lifetimePrestige() == 1,
                    "restart Prestige counters were not durable");
            require(snapshot.configRevision().orElseThrow().value().equals(revision),
                    "restart did not expose the exact active revision");
            eventually("restart Placeholder snapshot", () -> "novice".equals(renderPlaceholder(progressedPlayer)),
                    () -> {
                        pass("restart recovered exact revision, player state, providers, and Placeholder output");
                        advance();
                    });
        })));
        steps.add(() -> await("restart evaluation", service.evaluateRankUp(UUID.randomUUID()), result -> {
            require(result.successful() && result.value().orElseThrow().status()
                    == OperationEvaluationStatus.ELIGIBLE, "restart provider generation did not recompose");
            pass("late provider registration recomposed the exact stored revision without synthetic apply");
            advance();
        }));
        steps.add(() -> {
            pass("clean second-boot shutdown qualified");
            finishServer("PHASE8B-Q COMPLETE two-boot production qualification pass-count=" + passed);
        });
    }

    private Runnable commandStep(java.util.function.Supplier<String> command, String label) {
        return () -> command(command.get(), ignored -> {
            pass(label);
            advance();
        });
    }

    private void cancelUnknownPlayer() {
        cancelledPlayer = UUID.randomUUID();
        await("PRE cancellation", service.rankUp(cancelledPlayer), result -> {
            require(result.successful(), "cancelled operation must return a structured terminal value");
            OperationResult operation = result.value().orElseThrow();
            require(operation.status() == OperationStatus.BLOCKED && operation.durableOperationId().isEmpty(),
                    "cancelled PRE operation must not have a durable operation ID");
            OperationEventSnapshot pre = requireSnapshot(preRankEvents, cancelledPlayer, "cancelled PRE");
            require(pre.correlationId().equals(operation.requestId()),
                    "PRE-cancelled result changed the ingress request identity");
            await("cancelled player state", service.playerProgress(cancelledPlayer), progress -> {
                require(progress.successful() && progress.value().orElseThrow().stage().isEmpty(),
                        "PRE cancellation created durable player stage state");
                require(progress.value().orElseThrow().currentPrestige() == 0
                        && progress.value().orElseThrow().lifetimePrestige() == 0,
                        "PRE cancellation created durable Prestige state");
                require(!postRankPlayers.contains(cancelledPlayer), "POST rank event followed a cancelled PRE event");
                pass("PRE cancellation on unknown player had zero durable player-state effect");
                advance();
            });
        });
    }

    private void completeRankAndPrestige() {
        progressedPlayer = UUID.randomUUID();
        await("rank-up ingress", service.rankUp(progressedPlayer), rank -> {
            require(rank.successful(), "rank-up service failed");
            require(rank.value().orElseThrow().status() == OperationStatus.COMPLETED
                    && rank.value().orElseThrow().durableOperationId().isPresent(),
                    "rank-up did not complete durably");
            assertCorrelation(progressedPlayer, rank.value().orElseThrow(), preRankEvents, postRankEvents,
                    "rank-up");
            require(postRankPlayers.contains(progressedPlayer), "durable rank-up did not deliver POST");
            await("Prestige ingress", service.prestige(progressedPlayer), prestige -> {
                require(prestige.successful(), "Prestige service failed");
                require(prestige.value().orElseThrow().status() == OperationStatus.COMPLETED
                        && prestige.value().orElseThrow().durableOperationId().isPresent(),
                        "Prestige did not complete durably");
                assertCorrelation(progressedPlayer, prestige.value().orElseThrow(), prePrestigeEvents,
                        postPrestigeEvents, "Prestige");
                require(postPrestigePlayers.contains(progressedPlayer), "durable Prestige did not deliver POST");
                await("durable progress", service.playerProgress(progressedPlayer), progress -> {
                    PlayerProgressSnapshot snapshot = progress.value().orElseThrow();
                    require(snapshot.stage().orElseThrow().value().equals("novice"),
                            "Prestige did not reset the stage");
                    require(snapshot.currentPrestige() == 1 && snapshot.lifetimePrestige() == 1,
                            "Prestige counters are incorrect");
                    pass("rank-up and Prestige preserved one request ID across PRE, POST, and result while "
                            + "using distinct durable IDs");
                    advance();
                });
            });
        });
    }

    private void exerciseMultiKeyOmission() {
        omitOneMultiKeyResult = true;
        providerQueryCount.set(0);
        ProviderSnapshot snapshot = productionProviderSnapshot();
        List<MetricQuery> queries = List.of(
                new MetricQuery(new MetricId("external_points"), MetricReadMode.CURRENT, Map.of()),
                new MetricQuery(new MetricId("external_bonus"), MetricReadMode.CURRENT, Map.of()));
        CompletionStage<Throwable> omitted = productionMetricProvider().read(UUID.randomUUID(), queries,
                snapshot.generation()).handle((ignored, failure) -> failure);
        await("multi-key provider omission", omitted, failure -> {
            omitOneMultiKeyResult = false;
            require(failure != null, "incomplete multi-key provider result became a normal metric response");
            require(providerQueryCount.get() == 2,
                    "provider omission qualification did not receive one multi-key query batch");
            await("omission service fail-closed", service.evaluateRankUp(UUID.randomUUID()), result -> {
                require(result.successful() && result.value().orElseThrow().status()
                        != OperationEvaluationStatus.ELIGIBLE,
                        "provider contract violation did not degrade normal service evaluation");
                ProviderDeclaration omittedDeclaration = declaration;
                ProviderRegistrationHandle omittedHandle = handle;
                handle = null;
                await("omission provider reset", omittedHandle.unregister(), ignored -> {
                    getServer().getServicesManager().unregister(ProviderDeclaration.class, omittedDeclaration);
                    registerExternalProvider();
                    eventually("omission provider recovery", () -> handle != null && providerVisible(EXTERNAL_ID),
                            () -> eventuallyEvaluationEligible(() -> {
                                pass("incomplete multi-key provider result failed closed and exact response "
                                        + "recovered");
                                advance();
                            }));
                });
            });
        });
    }

    private MetricProvider productionMetricProvider() {
        Object registry = productionProviderRegistry();
        try {
            Method provider = registry.getClass().getMethod("provider",
                    net.maddkraft.maddprestige.api.id.ProviderId.class);
            Optional<?> value = (Optional<?>) provider.invoke(registry,
                    new net.maddkraft.maddprestige.api.id.ProviderId(EXTERNAL_ID));
            return (MetricProvider) value.orElseThrow();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot inspect the production provider binding", exception);
        }
    }

    private ProviderSnapshot productionProviderSnapshot() {
        Object registry = productionProviderRegistry();
        try {
            Method find = registry.getClass().getMethod("find",
                    net.maddkraft.maddprestige.api.id.ProviderId.class);
            Optional<?> value = (Optional<?>) find.invoke(registry,
                    new net.maddkraft.maddprestige.api.id.ProviderId(EXTERNAL_ID));
            return (ProviderSnapshot) value.orElseThrow();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot inspect the production provider generation", exception);
        }
    }

    private Object productionProviderRegistry() {
        try {
            org.bukkit.plugin.Plugin production = getServer().getPluginManager().getPlugin("MaddPrestige");
            require(production != null, "production plugin is absent");
            java.lang.reflect.Field runtimeField = production.getClass().getDeclaredField("runtime");
            require(runtimeField.trySetAccessible(), "production runtime field is inaccessible");
            Object runtime = runtimeField.get(production);
            java.lang.reflect.Field providersField = runtime.getClass().getDeclaredField("providers");
            require(providersField.trySetAccessible(), "production provider registry field is inaccessible");
            return providersField.get(runtime);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot inspect the production provider registry", exception);
        }
    }

    private void invalidateAuthorityDuringSuccessfulPre() {
        stalePrePlayer = UUID.randomUUID();
        await("successful PRE authority invalidation", service.rankUp(stalePrePlayer), result -> {
            require(result.successful(), "stale-PRE operation must return a structured result");
            OperationResult operation = result.value().orElseThrow();
            require(operation.status() == OperationStatus.BLOCKED && operation.durableOperationId().isEmpty(),
                    "stale-PRE operation did not fail before durability");
            OperationEventSnapshot pre = requireSnapshot(preRankEvents, stalePrePlayer, "stale PRE");
            require(pre.correlationId().equals(operation.requestId()),
                    "stale-PRE result changed the ingress request identity");
            require(!postRankEvents.containsKey(stalePrePlayer),
                    "stale-PRE operation incorrectly delivered POST");
            await("stale-PRE player state", service.playerProgress(stalePrePlayer), progress -> {
                PlayerProgressSnapshot snapshot = progress.value().orElseThrow();
                require(snapshot.stage().isEmpty() && snapshot.currentPrestige() == 0
                        && snapshot.lifetimePrestige() == 0,
                        "successful PRE followed by authority invalidation materialized player state");
                ProviderDeclaration staleDeclaration = declaration;
                getServer().getServicesManager().unregister(ProviderDeclaration.class, staleDeclaration);
                registerExternalProvider();
                eventually("stale-PRE provider recovery", () -> handle != null && providerVisible(EXTERNAL_ID), () ->
                        eventuallyEvaluationEligible(() -> {
                            pass("successful PRE authority invalidation stayed unjournaled and unmaterialized");
                            advance();
                        }));
            });
        });
    }

    private void exerciseProviderLifecycle() {
        int readsBefore = providerReads.get();
        ProviderRegistrationHandle prior = handle;
        handle = null;
        await("external provider unregister handle", prior.unregister(), ignored -> {
            getServer().getServicesManager().unregister(ProviderDeclaration.class, declaration);
            eventually("provider disappearance", () -> !providerVisible(EXTERNAL_ID), () ->
                    await("stale provider evaluation", service.evaluateRankUp(UUID.randomUUID()), blocked -> {
                        require(blocked.successful() && blocked.value().orElseThrow().status()
                                != OperationEvaluationStatus.ELIGIBLE,
                                "missing provider generation did not fail closed");
                        require(providerReads.get() == readsBefore,
                                "unregistered provider received another evaluation callback");
                        registerExternalProvider();
                        eventually("provider rebind", () -> handle != null && providerVisible(EXTERNAL_ID), () ->
                                eventuallyEvaluationEligible(() -> {
                                    require(revision.equals(service.playerProgress(progressedPlayer)
                                            .toCompletableFuture().join().value().orElseThrow()
                                            .configRevision().orElseThrow().value()),
                                            "provider rebind changed the authoritative revision");
                                    pass("external provider unregister failed closed and rebind recovered exact revision");
                                    advance();
                                }));
                    }));
        });
    }

    private void eventuallyEvaluationEligible(Runnable success) {
        eventually("provider-generation recomposition", () -> {
            ServiceResult<net.maddkraft.maddprestige.api.service.OperationEvaluation> result =
                    service.evaluateRankUp(UUID.randomUUID()).toCompletableFuture().join();
            return result.successful() && result.value().orElseThrow().status()
                    == OperationEvaluationStatus.ELIGIBLE;
        }, success);
    }

    private void finishFreshBoot() {
        Properties properties = new Properties();
        properties.setProperty("completed", "fresh-boot");
        properties.setProperty("revision", revision);
        properties.setProperty("player", progressedPlayer.toString());
        try (OutputStream output = java.nio.file.Files.newOutputStream(marker.toPath())) {
            properties.store(output, "Phase 8B first-party qualification state");
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        pass("first boot evidence sealed for exact-revision restart");
        finishServer("PHASE8B-Q FIRST-BOOT COMPLETE revision=" + revision + " pass-count=" + passed);
    }

    private void applyPlaceholderSetting(boolean enabled, Runnable success) {
        command("maddprestige config draft", draftLines -> {
            String draft = extract(UUID_PATTERN, draftLines);
            command("maddprestige config set " + draft
                    + " integrations.placeholderapi.output.enabled " + enabled, ignored ->
                    command("maddprestige config validate " + draft, validated ->
                    command("maddprestige config apply " + draft + " " + revision
                            + " phase8b placeholder reconcile", applied -> {
                                revision = extract(REVISION_PATTERN, applied);
                                success.run();
                            })));
        });
    }

    private void registerExternalProvider() {
        declaration = new QualificationProvider();
        getServer().getServicesManager().register(ProviderDeclaration.class, declaration, this,
                ServicePriority.Normal);
    }

    private boolean providerVisible(String id) {
        if (service == null) {
            return false;
        }
        ServiceResult<List<net.maddkraft.maddprestige.api.service.ProviderView>> result = service.providers();
        return result.successful() && result.value().orElseThrow().stream()
                .anyMatch(provider -> provider.id().value().equals(id));
    }

    private void registerLifecycleEvents() {
        Listener listener = new Listener() { };
        registerEvent("net.maddkraft.maddprestige.platform.paper.event.PreRankUpEvent", listener, event -> {
            OperationEventSnapshot snapshot = snapshot(event);
            preRankEvents.put(snapshot.playerId(), snapshot);
            if (snapshot.playerId().equals(stalePrePlayer)) {
                ProviderRegistrationHandle staleHandle = handle;
                require(staleHandle != null, "stale-PRE qualification provider handle was absent");
                handle = null;
                staleHandle.unregister().toCompletableFuture().join();
            }
            if (snapshot.playerId().equals(cancelledPlayer)) {
                ((Cancellable) event).setCancelled(true);
            }
        });
        registerEvent("net.maddkraft.maddprestige.platform.paper.event.PostRankUpEvent", listener,
                event -> {
                    OperationEventSnapshot snapshot = snapshot(event);
                    postRankPlayers.add(snapshot.playerId());
                    postRankEvents.put(snapshot.playerId(), snapshot);
                });
        registerEvent("net.maddkraft.maddprestige.platform.paper.event.PrePrestigeEvent", listener,
                event -> {
                    OperationEventSnapshot snapshot = snapshot(event);
                    prePrestigeEvents.put(snapshot.playerId(), snapshot);
                });
        registerEvent("net.maddkraft.maddprestige.platform.paper.event.PostPrestigeEvent", listener,
                event -> {
                    OperationEventSnapshot snapshot = snapshot(event);
                    postPrestigePlayers.add(snapshot.playerId());
                    postPrestigeEvents.put(snapshot.playerId(), snapshot);
                });
    }

    @SuppressWarnings("unchecked")
    private void registerEvent(String name, Listener listener, Consumer<Event> consumer) {
        try {
            Class<? extends Event> type = (Class<? extends Event>) Class.forName(name);
            getServer().getPluginManager().registerEvent(type, listener, EventPriority.NORMAL,
                    (ignored, event) -> consumer.accept(event), this, false);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Production lifecycle event is absent: " + name, exception);
        }
    }

    private static OperationEventSnapshot snapshot(Event event) {
        try {
            Method method = event.getClass().getMethod("snapshot");
            method.trySetAccessible();
            return (OperationEventSnapshot) method.invoke(event);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot read production lifecycle snapshot", exception);
        }
    }

    private static OperationEventSnapshot requireSnapshot(
            Map<UUID, OperationEventSnapshot> snapshots,
            UUID playerId,
            String label) {
        OperationEventSnapshot snapshot = snapshots.get(playerId);
        require(snapshot != null, label + " snapshot was not delivered");
        return snapshot;
    }

    private static void assertCorrelation(
            UUID playerId,
            OperationResult result,
            Map<UUID, OperationEventSnapshot> preEvents,
            Map<UUID, OperationEventSnapshot> postEvents,
            String label) {
        OperationEventSnapshot pre = requireSnapshot(preEvents, playerId, label + " PRE");
        OperationEventSnapshot post = requireSnapshot(postEvents, playerId, label + " POST");
        require(pre.correlationId().equals(result.requestId())
                && post.correlationId().equals(result.requestId()),
                label + " changed its ingress request identity across PRE, POST, or result");
        require(pre.durableOperationId().isEmpty(), label + " PRE exposed a durable identity");
        require(post.durableOperationId().equals(result.durableOperationId()),
                label + " POST and result disagree on durable identity");
        require(!result.durableOperationId().orElseThrow().value().equals(result.requestId()),
                label + " request and durable identities were not distinct");
    }

    private CommandSender commandSender() {
        return (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                new Class<?>[] {CommandSender.class}, (proxy, method, arguments) -> {
                    String name = method.getName();
                    if (name.equals("sendMessage") && arguments != null) {
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
                    return switch (name) {
                        case "hasPermission", "isPermissionSet", "isOp" -> true;
                        case "getName" -> "Phase8B-Harness";
                        case "getServer" -> getServer();
                        case "spigot" -> new CommandSender.Spigot();
                        case "toString" -> "Phase8B-Harness-CommandSender";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == arguments[0];
                        default -> defaultValue(method.getReturnType());
                    };
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            if (type == List.class) {
                return List.of();
            }
            if (type == Set.class) {
                return Set.of();
            }
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        return 0D;
    }

    private void command(String command, Consumer<List<String>> continuation) {
        messages.clear();
        String[] tokens = command.split(" +");
        require(tokens.length > 0 && tokens[0].equals("maddprestige"),
                "qualification command must use the MaddPrestige ingress");
        org.bukkit.command.PluginCommand registered = getServer().getPluginCommand("maddprestige");
        require(registered != null && registered.execute(sender, "maddprestige",
                java.util.Arrays.copyOfRange(tokens, 1, tokens.length)), "command was not accepted: " + command);
        eventually("command response: " + command, () -> !messages.isEmpty(), () -> {
            List<String> output = List.copyOf(messages);
            require(output.stream().noneMatch(Phase8BQualificationHarness::isFailureDiagnostic),
                    "command failed safely without completing: " + command + " output=" + output);
            continuation.accept(output);
        });
    }

    private static boolean isFailureDiagnostic(String line) {
        if (line.isEmpty() || line.charAt(0) != '[') {
            return false;
        }
        int closingBracket = line.indexOf(']');
        if (closingBracket < 0) {
            return false;
        }
        for (String marker : List.of("failed", "blocked", "unknown", "rejected", "stale")) {
            int markerIndex = line.indexOf(marker, 1);
            if (markerIndex >= 0 && markerIndex < closingBracket) {
                return true;
            }
        }
        return false;
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
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
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

    private String renderPlaceholder(UUID playerId) {
        return PlaceholderAPI.setPlaceholders(getServer().getOfflinePlayer(playerId), PLACEHOLDER);
    }

    private void pass(String message) {
        passed++;
        getLogger().info("PHASE8B-Q PASS " + passed + " " + message);
    }

    private void fail(String label, Throwable failure) {
        if (stopping) {
            return;
        }
        stopping = true;
        getLogger().log(java.util.logging.Level.SEVERE, "PHASE8B-Q FAIL " + label, failure);
        finishServer("PHASE8B-Q FAILED pass-count=" + passed);
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

    private static String requireProperty(Properties properties, String name) {
        String value = properties.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Qualification marker is missing " + name);
        }
        return value;
    }

    private static String extract(Pattern pattern, List<String> lines) {
        Matcher matcher = pattern.matcher(text(lines));
        if (!matcher.find()) {
            throw new IllegalStateException("Expected identity was absent from output: " + lines);
        }
        return matcher.group();
    }

    private static String text(List<String> lines) {
        return String.join("\n", lines);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private final class QualificationProvider implements ProviderDeclaration {
        @Override
        public ProviderMetadata metadata(ProviderCallContext context) {
            verifyContext(context, false);
            return new ProviderMetadata("external", "phase8b.provider.external", "1.0.0", List.of(
                    new ProviderMetricDefinition(new MetricId("external_points"), MetricValueType.COUNT,
                            Set.of(MetricOperator.GREATER_OR_EQUAL),
                            Set.of(MetricReadMode.CURRENT, MetricReadMode.LIFETIME), false,
                            MetricMonotonicity.MONOTONIC, MetricResetPolicy.NOT_APPLICABLE, Map.of(),
                            "phase8b.metric.external_points", "phase8b.metric.external_points.description",
                            "points", "authoritative"),
                    new ProviderMetricDefinition(new MetricId("external_bonus"), MetricValueType.COUNT,
                            Set.of(MetricOperator.GREATER_OR_EQUAL),
                            Set.of(MetricReadMode.CURRENT, MetricReadMode.LIFETIME), false,
                            MetricMonotonicity.MONOTONIC, MetricResetPolicy.NOT_APPLICABLE, Map.of(),
                            "phase8b.metric.external_bonus", "phase8b.metric.external_bonus.description",
                            "points", "authoritative")));
        }

        @Override
        public RequirementProvider requirements() {
            return (context, playerId, queries) -> {
                verifyContext(context, true);
                providerReads.incrementAndGet();
                providerQueryCount.set(queries.size());
                Map<ProviderMetricRequest, ProviderMetricResult> results = queries.stream()
                        .filter(query -> !omitOneMultiKeyResult
                                || !query.metricId().equals(new MetricId("external_bonus"))).collect(
                        java.util.stream.Collectors.toUnmodifiableMap(query -> query, query ->
                                ProviderMetricResult.available(MetricValue.count(10), Instant.now())));
                return CompletableFuture.completedFuture(results);
            };
        }

        @Override
        public void registered(ProviderRegistrationHandle registration) {
            handle = registration;
        }

        @Override
        public void unregistered() {
            // The handle is cleared by the test before intentional lifecycle invalidation.
        }

        private void verifyContext(ProviderCallContext context, boolean identified) {
            require(!Bukkit.isPrimaryThread(), "provider callback ran on the Paper server thread");
            require(context.execution() == ProviderExecutionExpectation.BOUNDED_WORKER,
                    "provider callback execution contract was not bounded-worker");
            require(context.ownerNamespace().equals("phase8b_harness"),
                    "provider owner namespace was not implementation-attested");
            require(context.providerId().isPresent() == identified,
                    "provider identity availability did not match callback lifetime");
            require(!context.cancellationRequested() && !context.expired(Instant.now()),
                    "provider callback began after cancellation/deadline");
        }
    }
}
