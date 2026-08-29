package net.maddkraft.qualification.phase8d;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;
import net.maddkraft.maddprestige.api.service.MaddPrestigeService;
import net.maddkraft.maddprestige.api.service.OperationEvaluation;
import net.maddkraft.maddprestige.api.service.OperationEvaluationStatus;
import net.maddkraft.maddprestige.api.service.OperationResult;
import net.maddkraft.maddprestige.api.service.OperationStatus;
import net.maddkraft.maddprestige.api.service.PlayerProgressSnapshot;
import net.maddkraft.maddprestige.api.service.ServiceResult;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventException;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.scheduler.BukkitTask;

/** Qualifies the exact public A70 profile through a fresh process and unchanged restart. */
public final class Phase8DQualificationHarness extends JavaPlugin {
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern REVISION_PATTERN = Pattern.compile("r_[0-9a-f]{32}");
    private static final Set<String> MANAGED_GROUPS = Set.of("Member", "Adventurer", "Veteran");
    private final CopyOnWriteArrayList<String> messages = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> dormantWarnings = new CopyOnWriteArrayList<>();
    private final ArrayDeque<Runnable> steps = new ArrayDeque<>();
    private CommandSender sender;
    private MaddPrestigeService service;
    private LuckPerms luckPerms;
    private java.io.File marker;
    private UUID setupSession;
    private UUID playerId;
    private UUID dormantPlayerId;
    private String revision;
    private int passed;
    private boolean stopping;
    private Handler dormantLogHandler;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        marker = new java.io.File(getDataFolder(), "qualification.properties");
        sender = commandSender();
        getServer().getScheduler().runTaskLater(this, this::begin, 40L);
    }

    @Override
    public void onDisable() {
        org.bukkit.plugin.Plugin production = getServer().getPluginManager().getPlugin("MaddPrestige");
        if (production != null && dormantLogHandler != null) {
            production.getLogger().removeHandler(dormantLogHandler);
        }
        getLogger().info("PHASE8D-Q shutdown pass-count=" + passed);
    }

    private void begin() {
        try {
            if (marker.isFile() && "fresh-boot".equals(loadMarker().getProperty("completed"))) {
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
        steps.add(() -> eventually("production services", () -> {
            service = getServer().getServicesManager().load(MaddPrestigeService.class);
            luckPerms = getServer().getServicesManager().load(LuckPerms.class);
            return service != null && luckPerms != null && providerVisible("luckperms")
                    && providerVisible("paper_statistics");
        }, () -> {
            captureDormantWarnings();
            require(service.stages().successful() && service.stages().value().orElseThrow().isEmpty(),
                    "fresh server was not dormant");
            pass("fresh SQLite/server directory published the exact production services in dormant mode");
            advance();
        }));
        steps.add(this::qualifyDormantOnlineIntervals);
        steps.add(() -> {
            for (String group : List.of("Member", "Adventurer", "Veteran")) {
                require(getServer().dispatchCommand(getServer().getConsoleSender(), "lp creategroup " + group),
                        "LuckPerms rejected group creation command for " + group);
            }
            eventually("external groups", () -> MANAGED_GROUPS.stream()
                    .allMatch(group -> luckPerms.getGroupManager().getGroup(group) != null), () -> {
                pass("administrator-created Member/Adventurer/Veteran groups exist; MaddPrestige created none");
                advance();
            });
        });
        steps.add(() -> command("maddprestige setup discover", lines -> {
            require(text(lines).contains("Active V2 configuration present: false"), "discovery was not dormant");
            pass("public Quick Start discovery succeeded");
            advance();
        }));
        steps.add(() -> command("maddprestige setup start", lines -> {
            setupSession = UUID.fromString(extract(UUID_PATTERN, lines));
            pass("public setup session started");
            advance();
        }));
        steps.add(commandStep(() -> "maddprestige setup provider luckperms",
                "current-session LuckPerms provider selected without UUID repetition"));
        steps.add(commandStep(() -> "maddprestige setup stage member Member Member",
                "Member baseline stage added through the guided form"));
        steps.add(commandStep(() -> "maddprestige setup stage adventurer Adventurer Adventurer",
                "Adventurer stage added through the guided form"));
        steps.add(commandStep(() -> "maddprestige setup stage veteran Veteran Veteran",
                "Veteran stage added through the guided form"));
        steps.add(commandStep(() -> "maddprestige setup baseline member",
                "Member baseline selected through the guided form"));
        steps.add(commandStep(() -> "maddprestige setup playtime adventurer PT1M",
                "typed 60-second current-Prestige requirement generated for Adventurer"));
        steps.add(commandStep(() -> "maddprestige setup playtime veteran PT3M",
                "typed 180-second current-Prestige requirement generated for Veteran"));
        steps.add(commandStep(() -> "maddprestige setup prestige enabled veteran member",
                "contextual Prestige syntax configured Veteran eligibility and Member reset"));
        steps.add(() -> command("maddprestige setup preview", lines -> {
            String output = text(lines);
            require(output.contains("Validation: VALID") && output.contains("adventurer_playtime")
                    && output.contains("veteran_playtime"), "preview was not the exact valid guided public profile");
            pass("guided setup preview independently validated the exact three-stage profile");
            advance();
        }));
        steps.add(() -> command("maddprestige setup acknowledge", lines -> {
            setupSession = UUID.fromString(extract(UUID_PATTERN, lines));
            pass("activation risk was explicitly acknowledged using the server token");
            advance();
        }));
        steps.add(() -> command("maddprestige setup confirm " + setupSession + " Initial generic progression setup",
                lines -> {
                    revision = extract(REVISION_PATTERN, lines);
                    pass("exact acknowledged profile applied as " + revision);
                    advance();
                }));
        steps.add(() -> eventually("active three-stage profile", () -> service.stages().successful()
                && service.stages().value().orElseThrow().size() == 3, () ->
                command("maddprestige doctor", lines -> {
                    require(text(lines).contains("Doctor: HEALTHY"), "Doctor was not healthy: " + lines);
                    pass("Doctor reported the active profile healthy");
                    advance();
                })));
        steps.add(this::qualifyInitialAndInsufficientAdventurer);
        steps.add(this::completeAdventurer);
        steps.add(this::qualifyInsufficientVeteran);
        steps.add(this::completeVeteran);
        steps.add(this::completePrestige);
        steps.add(this::qualifyReplayAndOfflineState);
        steps.add(this::finishFreshBoot);
    }

    private void buildRestartSteps(Properties properties) {
        revision = required(properties, "revision");
        playerId = UUID.fromString(required(properties, "player"));
        long operations = Long.parseLong(required(properties, "operations"));
        long stageHistory = Long.parseLong(required(properties, "stageHistory"));
        long prestigeHistory = Long.parseLong(required(properties, "prestigeHistory"));
        steps.add(() -> eventually("restart composition", () -> {
            service = getServer().getServicesManager().load(MaddPrestigeService.class);
            luckPerms = getServer().getServicesManager().load(LuckPerms.class);
            return service != null && luckPerms != null && service.stages().successful()
                    && service.stages().value().orElseThrow().size() == 3;
        }, () -> await("restart player state", service.playerProgress(playerId), result -> {
            PlayerProgressSnapshot state = value(result, "restart progress");
            require(state.stage().orElseThrow().value().equals("member") && state.currentPrestige() == 1
                    && state.lifetimePrestige() == 1, "restart player state changed");
            require(state.configRevision().orElseThrow().value().equals(revision), "active revision changed");
            require(getServer().getOfflinePlayer(playerId).getStatistic(Statistic.PLAY_ONE_MINUTE) == 3600,
                    "Paper lifetime statistic changed across restart");
            require(dbCount("mp_operations", playerId) == operations
                    && dbCount("mp_stage_history", playerId) == stageHistory
                    && dbCount("mp_prestige_history", playerId) == prestigeHistory,
                    "durable history counts changed across restart");
            loadManagedGroups(playerId, groups -> {
                require(groups.equals(Set.of("Member")), "restart LuckPerms projection is not Member: " + groups);
                pass("unchanged restart recovered stage Member, Prestige 1, exact revision/history/statistic/LP state");
                advance();
            });
        })));
        steps.add(() -> await("restart scoped progress", service.evaluateRankUp(playerId), result -> {
            OperationEvaluation evaluation = value(result, "restart evaluation");
            require(evaluation.status() == OperationEvaluationStatus.BLOCKED,
                    "new Prestige baseline did not retain zero scoped progress");
            pass("restart retained the current-Prestige baseline and blocked premature Adventurer");
            advance();
        }));
        steps.add(() -> command("maddprestige doctor", lines -> {
            require(text(lines).contains("Doctor: HEALTHY"), "restart Doctor was not healthy: " + lines);
            pass("restart Doctor remained healthy");
            finishServer("PHASE8D-Q COMPLETE two-boot A70 qualification pass-count=" + passed);
        }));
    }

    private void qualifyInitialAndInsufficientAdventurer() {
        playerId = dormantPlayerId;
        OfflinePlayer player = getServer().getOfflinePlayer(playerId);
        player.setStatistic(Statistic.PLAY_ONE_MINUTE, 0);
        await("stable first progress read", service.playerProgress(playerId), firstRead -> {
            PlayerProgressSnapshot initial = value(firstRead, "stable first progress read");
            require(initial.stage().orElseThrow().value().equals("member"),
                    "stable first progress read did not establish durable Member");
            loadManagedGroups(playerId, groups -> {
                require(groups.equals(Set.of("Member")),
                        "fresh stable read did not project real LuckPerms Member: " + groups);
                require(dbCount("mp_operations", playerId) == 1 && dbCount("mp_stage_history", playerId) == 0,
                        "initial Member projection was not one journaled non-transition operation");
                require(dormantWarnings.isEmpty(), "dormant warning recurred after live activation: "
                        + dormantWarnings);
                pass("the same dormant player initialized after live activation without restart or stale marker");
                qualifyRepeatedInitialization();
            });
        });
    }

    private void qualifyDormantOnlineIntervals() {
        dormantPlayerId = UUID.randomUUID();
        Player player = player(dormantPlayerId);
        final int[] intervals = {0};
        final BukkitTask[] task = new BukkitTask[1];
        task[0] = getServer().getScheduler().runTaskTimer(this, () -> {
            PlayerJoinEvent event = new PlayerJoinEvent(player, (Component) null);
            for (RegisteredListener listener : PlayerJoinEvent.getHandlerList().getRegisteredListeners()) {
                if (listener.getPlugin().getName().equals("MaddPrestige")) {
                    try {
                        listener.callEvent(event);
                    } catch (EventException exception) {
                        throw new IllegalStateException("MaddPrestige join listener failed", exception);
                    }
                }
            }
            intervals[0]++;
            if (intervals[0] == 6) {
                task[0].cancel();
                getServer().getScheduler().runTaskLater(this, () -> {
                    try {
                        require(dormantWarnings.isEmpty(), "dormant placeholder warning was emitted: "
                                + dormantWarnings);
                        require(playerLifecycleRows(dormantPlayerId) == 0,
                                "dormant placeholder ingress persisted canonical player state");
                        pass("six one-second dormant player listener intervals emitted zero warnings and state");
                        advance();
                    } catch (Throwable failure) {
                        fail("dormant player intervals", failure);
                    }
                }, 20L);
            }
        }, 1L, 20L);
    }

    private void captureDormantWarnings() {
        org.bukkit.plugin.Plugin production = getServer().getPluginManager().getPlugin("MaddPrestige");
        require(production != null, "production plugin is absent");
        dormantLogHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()
                        && record.getMessage().contains("Placeholder snapshot")) {
                    dormantWarnings.add(record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        production.getLogger().addHandler(dormantLogHandler);
    }

    private long playerLifecycleRows(UUID target) {
        String sql = "SELECT (SELECT COUNT(*) FROM mp_player_stage_state WHERE player_uuid = ?) "
                + "+ (SELECT COUNT(*) FROM mp_player_prestige_state WHERE player_uuid = ?)";
        try (Connection connection = sqliteConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, target.toString());
            statement.setString(2, target.toString());
            try (ResultSet rows = statement.executeQuery()) {
                require(rows.next(), "player lifecycle count query returned no row");
                return rows.getLong(1);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot inspect disposable player lifecycle state", exception);
        }
    }

    private Player player(UUID id) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getName" -> "DormantA76Player";
                    case "getServer" -> getServer();
                    case "isOnline" -> true;
                    case "toString" -> "DormantA76Player[" + id + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> defaultValue(method.getReturnType());
                });
    }

    private void qualifyRepeatedInitialization() {
        long operations = dbCount("mp_operations", playerId);
        await("repeated lifecycle read", service.playerProgress(playerId), repeated -> {
            require(value(repeated, "repeated lifecycle read").stage().orElseThrow().value().equals("member"),
                    "repeated lifecycle read changed the baseline stage");
            loadManagedGroups(playerId, groups -> {
                require(groups.equals(Set.of("Member")) && dbCount("mp_operations", playerId) == operations,
                        "repeated lifecycle initialization duplicated the Member projection");
                pass("repeated lifecycle establishment was idempotent with no duplicate projection mutation");
                qualifyInitialBlock();
            });
        });
    }

    private void qualifyInitialBlock() {
        long beforeOperations = dbCount("mp_operations", playerId);
        long beforeStages = dbCount("mp_stage_history", playerId);
        await("initial evaluation", service.evaluateRankUp(playerId), result -> {
            OperationEvaluation evaluation = value(result, "initial evaluation");
            require(evaluation.status() == OperationEvaluationStatus.BLOCKED
                    && evaluation.targetStage().orElseThrow().value().equals("adventurer"),
                    "durable Member did not target Adventurer");
            await("insufficient rank request", service.rankUp(playerId), blocked -> {
                OperationResult operation = value(blocked, "insufficient rank request");
                require(operation.status() == OperationStatus.BLOCKED && operation.durableOperationId().isEmpty(),
                        "insufficient request claimed a durable operation");
                command("maddprestige why rankup " + playerId, why -> {
                    String output = text(why);
                    require(output.contains("playtime_60_seconds") || output.contains("PT1M"),
                            "Why omitted the exact one-minute blocker: " + why);
                    require(dbCount("mp_operations", playerId) == beforeOperations
                            && dbCount("mp_stage_history", playerId) == beforeStages,
                            "insufficient Adventurer attempt changed durable state/history");
                    pass("durable Member insufficient Adventurer and Why were actionable "
                            + "with zero effects");
                    qualifyAdditionalIngresses();
                });
            });
        });
    }

    private void qualifyAdditionalIngresses() {
        UUID whyPlayer = UUID.randomUUID();
        getServer().getOfflinePlayer(whyPlayer).setStatistic(Statistic.PLAY_ONE_MINUTE, 0);
        command("maddprestige why rankup " + whyPlayer, ignored -> loadManagedGroups(whyPlayer, groups -> {
            require(groups.equals(Set.of("Member")), "Why ingress did not establish real Member: " + groups);
            pass("brand-new Why ingress established real Member before explaining progress");
            UUID previewPlayer = UUID.randomUUID();
            getServer().getOfflinePlayer(previewPlayer).setStatistic(Statistic.PLAY_ONE_MINUTE, 0);
            command("maddprestige simulate rankup " + previewPlayer,
                    preview -> loadManagedGroups(previewPlayer, previewGroups -> {
                        require(previewGroups.equals(Set.of("Member")),
                                "preview ingress did not establish real Member: " + previewGroups);
                        pass("brand-new preview ingress established real Member before returning its result");
                        UUID operationPlayer = UUID.randomUUID();
                        getServer().getOfflinePlayer(operationPlayer).setStatistic(Statistic.PLAY_ONE_MINUTE, 0);
                        await("brand-new operation ingress", service.rankUp(operationPlayer), result -> {
                            require(value(result, "brand-new operation ingress").status() == OperationStatus.BLOCKED,
                                    "brand-new insufficient operation did not block");
                            loadManagedGroups(operationPlayer, operationGroups -> {
                                require(operationGroups.equals(Set.of("Member")),
                                        "operation ingress did not establish real Member: " + operationGroups);
                                pass("brand-new progression operation ingress established real Member before blocking");
                                advance();
                            });
                        });
                    }));
        }));
    }

    private void completeAdventurer() {
        getServer().getOfflinePlayer(playerId).setStatistic(Statistic.PLAY_ONE_MINUTE, 1200);
        await("Member to Adventurer", service.rankUp(playerId), result -> {
            requireCompleted(value(result, "Adventurer rank-up"), "Member to Adventurer");
            await("Adventurer state", service.playerProgress(playerId), progress -> {
                require(value(progress, "Adventurer state").stage().orElseThrow().value().equals("adventurer"),
                        "durable stage is not Adventurer");
                loadManagedGroups(playerId, groups -> {
                    require(groups.equals(Set.of("Adventurer")), "managed group is not exactly Adventurer: " + groups);
                    require(dbCount("mp_operations", playerId) == 2 && dbCount("mp_stage_history", playerId) == 1,
                            "Adventurer operation/history count is incoherent");
                    pass("offline rank path completed Member to Adventurer exactly once with one LP managed group");
                    advance();
                });
            });
        });
    }

    private void qualifyInsufficientVeteran() {
        getServer().getOfflinePlayer(playerId).setStatistic(Statistic.PLAY_ONE_MINUTE, 2000);
        long operations = dbCount("mp_operations", playerId);
        long history = dbCount("mp_stage_history", playerId);
        await("insufficient Veteran", service.rankUp(playerId), result -> {
            OperationResult operation = value(result, "insufficient Veteran");
            require(operation.status() == OperationStatus.BLOCKED && operation.durableOperationId().isEmpty(),
                    "insufficient Veteran attempt became durable");
            command("maddprestige why rankup " + playerId, why -> {
                String output = text(why);
                require(output.contains("playtime_180_seconds") || output.contains("PT3M"),
                        "Why omitted the exact three-minute blocker: " + why);
                require(dbCount("mp_operations", playerId) == operations
                        && dbCount("mp_stage_history", playerId) == history,
                        "insufficient Veteran attempt changed durable state/history");
                pass("below 180 seconds Veteran request failed with exact Why evidence and zero effects");
                advance();
            });
        });
    }

    private void completeVeteran() {
        getServer().getOfflinePlayer(playerId).setStatistic(Statistic.PLAY_ONE_MINUTE, 3600);
        await("Adventurer to Veteran", service.rankUp(playerId), result -> {
            requireCompleted(value(result, "Veteran rank-up"), "Adventurer to Veteran");
            await("Veteran state", service.playerProgress(playerId), progress -> {
                require(value(progress, "Veteran state").stage().orElseThrow().value().equals("veteran"),
                        "durable stage is not Veteran");
                loadManagedGroups(playerId, groups -> {
                    require(groups.equals(Set.of("Veteran")), "managed group is not exactly Veteran: " + groups);
                    require(dbCount("mp_operations", playerId) == 3 && dbCount("mp_stage_history", playerId) == 2,
                            "Veteran operation/history count is incoherent");
                    pass("Adventurer advanced to Veteran exactly once with coherent LP and durable history");
                    advance();
                });
            });
        });
    }

    private void completePrestige() {
        int paperStatistic = getServer().getOfflinePlayer(playerId).getStatistic(Statistic.PLAY_ONE_MINUTE);
        await("Prestige", service.prestige(playerId), result -> {
            requireCompleted(value(result, "Prestige"), "Prestige");
            await("Prestige state", service.playerProgress(playerId), progress -> {
                PlayerProgressSnapshot state = value(progress, "Prestige state");
                require(state.stage().orElseThrow().value().equals("member") && state.currentPrestige() == 1
                        && state.lifetimePrestige() == 1, "Prestige state/counters are incorrect");
                require(getServer().getOfflinePlayer(playerId).getStatistic(Statistic.PLAY_ONE_MINUTE) == paperStatistic,
                        "Prestige reset Paper-owned lifetime statistic");
                loadManagedGroups(playerId, groups -> {
                    require(groups.equals(Set.of("Member")), "Prestige LP projection is not Member: " + groups);
                    require(dbCount("mp_operations", playerId) == 4 && dbCount("mp_stage_history", playerId) == 3
                            && dbCount("mp_prestige_history", playerId) == 1,
                            "Prestige durable history is incoherent");
                    await("post-Prestige baseline", service.evaluateRankUp(playerId), evaluation -> {
                        require(value(evaluation, "post-Prestige evaluation").status()
                                == OperationEvaluationStatus.BLOCKED,
                                "current-Prestige progress did not reset to zero");
                        pass("Prestige incremented once, reset Member/LP/baseline, preserved Paper statistic/history");
                        advance();
                    });
                });
            });
        });
    }

    private void qualifyReplayAndOfflineState() {
        long operations = dbCount("mp_operations", playerId);
        long stages = dbCount("mp_stage_history", playerId);
        long prestiges = dbCount("mp_prestige_history", playerId);
        await("semantic replay", service.prestige(playerId), result -> {
            OperationResult replay = value(result, "semantic replay");
            require(replay.status() == OperationStatus.BLOCKED && replay.durableOperationId().isEmpty(),
                    "repeated Prestige created another durable identity");
            require(dbCount("mp_operations", playerId) == operations && dbCount("mp_stage_history", playerId) == stages
                    && dbCount("mp_prestige_history", playerId) == prestiges,
                    "repeated facade request duplicated state/history");
            pass("facade exposes no caller idempotency token; repeated terminal request produced no duplicate effect");
            pass("supported offline UUID statistic/rank/projection path completed deterministically without a client");
            advance();
        });
    }

    private void finishFreshBoot() {
        Properties properties = new Properties();
        properties.setProperty("completed", "fresh-boot");
        properties.setProperty("revision", revision);
        properties.setProperty("player", playerId.toString());
        properties.setProperty("operations", Long.toString(dbCount("mp_operations", playerId)));
        properties.setProperty("stageHistory", Long.toString(dbCount("mp_stage_history", playerId)));
        properties.setProperty("prestigeHistory", Long.toString(dbCount("mp_prestige_history", playerId)));
        try (OutputStream output = java.nio.file.Files.newOutputStream(marker.toPath())) {
            properties.store(output, "Phase 8D A70 first boot state");
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        pass("fresh-boot evidence sealed for unchanged restart");
        finishServer("PHASE8D-Q FIRST-BOOT COMPLETE revision=" + revision + " pass-count=" + passed);
    }

    private boolean providerVisible(String id) {
        if (service == null) {
            return false;
        }
        var providers = service.providers();
        return providers.successful() && providers.value().orElseThrow().stream()
                .anyMatch(provider -> provider.id().value().equals(id));
    }

    private void loadManagedGroups(UUID target, Consumer<Set<String>> continuation) {
        boolean loaded = luckPerms.getUserManager().isLoaded(target);
        luckPerms.getUserManager().loadUser(target).whenComplete((user, failure) ->
                getServer().getScheduler().runTask(this, () -> {
                    if (failure != null) {
                        fail("LuckPerms user load", failure);
                        return;
                    }
                    Set<String> groups = user.data().toCollection().stream()
                            .filter(InheritanceNode.class::isInstance).map(InheritanceNode.class::cast)
                            .filter(node -> !node.hasExpiry() && node.getContexts().isEmpty())
                            .map(InheritanceNode::getGroupName)
                            .map(name -> MANAGED_GROUPS.stream().filter(group -> group.equalsIgnoreCase(name))
                                    .findFirst())
                            .flatMap(Optional::stream)
                            .collect(java.util.stream.Collectors.toUnmodifiableSet());
                    if (!loaded) {
                        luckPerms.getUserManager().cleanupUser(user);
                    }
                    try {
                        continuation.accept(groups);
                    } catch (Throwable exception) {
                        fail("LuckPerms state continuation", exception);
                    }
                }));
    }

    private long dbCount(String table, UUID target) {
        HistoryQuery query = HistoryQuery.forTable(table);
        try (Connection connection = sqliteConnection();
                PreparedStatement statement = connection.prepareStatement(query.sql())) {
            statement.setString(1, target.toString());
            try (ResultSet rows = statement.executeQuery()) {
                require(rows.next(), "count query returned no row");
                return rows.getLong(1);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot inspect disposable qualification history", exception);
        }
    }

    private Connection sqliteConnection() throws Exception {
        org.bukkit.plugin.Plugin production = getServer().getPluginManager().getPlugin("MaddPrestige");
        require(production != null, "production plugin is absent");
        Class<?> type = Class.forName("org.sqlite.JDBC", true, production.getClass().getClassLoader());
        Driver driver = (Driver) type.getConstructor().newInstance();
        Path database = production.getDataFolder().toPath().resolve("maddprestige-v2.sqlite");
        Connection connection = driver.connect("jdbc:sqlite:" + database.toAbsolutePath(), new Properties());
        if (connection == null) {
            throw new IllegalStateException("SQLite driver rejected disposable database URL");
        }
        return connection;
    }

    private enum HistoryQuery {
        OPERATIONS("mp_operations", "SELECT COUNT(*) FROM mp_operations WHERE target_uuid = ?"),
        STAGE_HISTORY("mp_stage_history", "SELECT COUNT(*) FROM mp_stage_history WHERE player_uuid = ?"),
        PRESTIGE_HISTORY("mp_prestige_history", "SELECT COUNT(*) FROM mp_prestige_history WHERE player_uuid = ?");

        private final String table;
        private final String sql;

        HistoryQuery(String table, String sql) {
            this.table = table;
            this.sql = sql;
        }

        private static HistoryQuery forTable(String table) {
            for (HistoryQuery query : values()) {
                if (query.table.equals(table)) {
                    return query;
                }
            }
            throw new IllegalArgumentException("unexpected qualification history table");
        }

        private String sql() {
            return sql;
        }
    }

    private Runnable commandStep(java.util.function.Supplier<String> command, String label) {
        return () -> command(command.get(), ignored -> {
            pass(label);
            advance();
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
                        case "getName" -> "Phase8D-Harness";
                        case "getServer" -> getServer();
                        case "spigot" -> new CommandSender.Spigot();
                        case "toString" -> "Phase8D-Harness-CommandSender";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == arguments[0];
                        default -> defaultValue(method.getReturnType());
                    };
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return type == List.class ? List.of() : type == Set.class ? Set.of() : null;
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

    private void command(String command, Consumer<List<String>> continuation) {
        messages.clear();
        String[] tokens = command.split(" +");
        require(tokens.length > 0 && tokens[0].equals("maddprestige"), "unexpected command ingress");
        var registered = getServer().getPluginCommand("maddprestige");
        require(registered != null && registered.execute(sender, "maddprestige",
                java.util.Arrays.copyOfRange(tokens, 1, tokens.length)), "command rejected: " + command);
        eventually("command response " + command, () -> !messages.isEmpty(), () ->
                continuation.accept(List.copyOf(messages)));
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
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
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
        getLogger().info("PHASE8D-Q PASS " + passed + " " + message);
    }

    private void fail(String label, Throwable failure) {
        if (stopping) return;
        stopping = true;
        getLogger().log(java.util.logging.Level.SEVERE, "PHASE8D-Q FAIL " + label, failure);
        finishServer("PHASE8D-Q FAILED pass-count=" + passed);
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

    private static String required(Properties properties, String name) {
        String value = properties.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("marker missing " + name);
        return value;
    }

    private static String extract(Pattern pattern, List<String> lines) {
        Matcher matcher = pattern.matcher(text(lines));
        if (!matcher.find()) throw new IllegalStateException("expected identity absent from " + lines);
        return matcher.group();
    }

    private static String text(List<String> lines) {
        return String.join("\n", lines);
    }

    private static <T> T value(ServiceResult<T> result, String label) {
        require(result.successful(), label + " service result failed: " + result.error());
        return result.value().orElseThrow();
    }

    private static void requireCompleted(OperationResult result, String label) {
        require(result.status() == OperationStatus.COMPLETED && result.durableOperationId().isPresent(),
                label + " did not complete durably: " + result);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
