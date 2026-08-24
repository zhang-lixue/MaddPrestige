package net.maddkraft.qualification.phase8e;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.regex.Pattern;
import net.maddkraft.maddprestige.api.event.OperationEventSnapshot;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.service.MaddPrestigeService;
import net.maddkraft.maddprestige.api.service.OperationEvaluationStatus;
import net.maddkraft.maddprestige.api.service.OperationResult;
import net.maddkraft.maddprestige.api.service.OperationStatus;
import net.maddkraft.maddprestige.api.service.PlayerProgressSnapshot;
import net.maddkraft.maddprestige.api.service.ProviderView;
import net.maddkraft.maddprestige.api.service.ServiceResult;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;

/** Real-Paper fault matrix driven only through Stable services, events, plugin lifecycle and owned services. */
final class Phase8EFaultQualification {
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern REVISION_PATTERN = Pattern.compile("r_[0-9a-f]{32}");
    private static final String ALPHA = "phase8e_alpha:alpha";
    private static final String BETA = "phase8e_beta:beta";
    private static final String COMMAND_NAME = Phase8EQualificationSupport.COMMAND_NAME;
    private static final String PLUGIN_NAME = "MaddPrestige";
    private static final String SETUP_START = "maddprestige setup start";
    private static final String INTERNAL_PROVIDER_SELECTION = " internal";
    private static final String BASE_STAGE_ARGUMENTS = " base Base";
    private static final String BASE_STAGE_ID = " base";
    private static final String SETUP_STAGE = "maddprestige setup stage ";
    private static final String SETUP_BASELINE = "maddprestige setup baseline ";
    private static final String SETUP_REQUIREMENT = "maddprestige setup requirement ";
    private static final String SETUP_PRESTIGE = "maddprestige setup prestige ";
    private static final String SETUP_PREVIEW = "maddprestige setup preview ";
    private static final String SETUP_ACKNOWLEDGE = "maddprestige setup acknowledge ";
    private static final String SETUP_CONFIRM = "maddprestige setup confirm ";
    private static final String ALPHA_COMMAND = "phase8ealpha";
    private static final String PROVIDER_MODE_COMMAND = "mode";
    private static final String REBIND_COMMAND = "rebind";
    private static final String SETUP_PROVIDER = "maddprestige setup provider ";
    private static final String BETA_COMMAND = "phase8ebeta";
    private static final String HEALTHY_MODE = "HEALTHY";
    private static final String SUCCESS_MODE = "SUCCESS";
    private static final String ALPHA_LABEL = "Alpha ";
    private static final String MCMO_PROVIDER = "mcmmo";
    private static final String GRIEF_PREVENTION_CLAIMS_PROVIDER = "griefprevention_claims";
    private static final String WORLD_GUARD_REGION_PROVIDER = "worldguard_region";
    private static final String CRAFT_ENGINE_ITEM_COUNT_PROVIDER = "craftengine_item_count";
    private static final String CRAFT_ENGINE_ITEM_REWARD_PROVIDER = "craftengine_item_reward";
    private static final String GRIEF_PREVENTION_REWARD_PROVIDER = "griefprevention_claim_blocks_reward";
    private static final String MANUAL_PROGRESS_PROVIDER = "phase5_events";
    private static final String OBSERVED_SYNCHRONOUS_ACTIVE = "observedSynchronousActive";
    private static final String OBSERVED_REGISTRATIONS = "observedRegistrations";
    private static final Set<String> VAULT_PROVIDERS = Set.of(
            "vault_balance", "vault_economy_cost", "vault_economy_reward");
    private static final Map<String, String> EXACT_DEPENDENCIES = Map.of(
            "LuckPerms", "5.5.71",
            "Vault", "2.20.2",
            "mcMMO", "2.2.053",
            "PlaceholderAPI", "2.12.2",
            "EconomyShopGUI", "7.2.0",
            "QuickShop-Hikari", String.join(".", "6", "2", "0", "11"),
            "GriefPrevention", "16.18.7",
            "WorldEdit", "7.4.4+7546-f9e033f",
            "WorldGuard", "7.0.18+2392-fa605e6",
            "CraftEngine", "26.7.4");

    private final JavaPlugin plugin;
    private final String mode;
    private final ArrayDeque<Runnable> steps = new ArrayDeque<>();
    private final CopyOnWriteArrayList<String> messages = new CopyOnWriteArrayList<>();
    private final Map<UUID, OperationEventSnapshot> preRank = new ConcurrentHashMap<>();
    private final Map<UUID, OperationEventSnapshot> postRank = new ConcurrentHashMap<>();
    private final Map<UUID, OperationEventSnapshot> prePrestige = new ConcurrentHashMap<>();
    private final Map<UUID, OperationEventSnapshot> postPrestige = new ConcurrentHashMap<>();
    private final Map<String, Long> measuredMillis = new ConcurrentHashMap<>();
    private final Phase8EQualificationSupport.Coordinator coordinator;
    private final CommandSender sender;
    private MaddPrestigeService service;
    private Economy economy;
    private Plugin economyController;
    private CompletionStage<ServiceResult<OperationResult>> recursiveRank;
    private CompletionStage<ServiceResult<OperationResult>> recursivePrestige;
    private CompletionStage<ServiceResult<OperationResult>> crossPlayerRank;
    private UUID cancelRankPlayer;
    private UUID throwPreRankPlayer;
    private UUID throwPostRankPlayer;
    private UUID recursivePlayer;
    private UUID crossPlayer;
    private UUID rebindInPrePlayer;
    private UUID loseEconomyInPrePlayer;
    private UUID setupSession;
    private String revision;
    private int passed;
    private boolean stopping;

    Phase8EFaultQualification(JavaPlugin plugin, String mode) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.mode = java.util.Objects.requireNonNull(mode, PROVIDER_MODE_COMMAND);
        coordinator = new Phase8EQualificationSupport.Coordinator(plugin, steps, messages,
                "Phase8E-Fault-Harness", true,
                transcript -> plugin.getLogger().info("PHASE8E-COMMAND " + transcript), this::fail);
        sender = coordinator.sender();
    }

    void enable() {
        registerLifecycleEvents();
        plugin.getServer().getScheduler().runTaskLater(plugin, this::begin, 80L);
    }

    void disable() {
        int withdrawalCount = economyController == null ? 0 : withdrawals();
        int depositCount = economyController == null ? 0 : deposits();
        plugin.getLogger().info("PHASE8E-FAULT shutdown mode=" + mode + " pass-count=" + passed
                + " withdrawals=" + withdrawalCount + " deposits=" + depositCount
                + " timings-ms=" + measuredMillis);
    }

    private void begin() {
        try {
            if ("fault".equals(mode)) {
                buildFaultSteps();
            } else if ("absence".equals(mode)) {
                buildAbsenceSteps();
            } else if ("setup-audit".equals(mode)) {
                buildSetupAuditSteps();
            } else {
                throw new IllegalStateException("Unsupported Phase 8E qualification mode: " + mode);
            }
            coordinator.advance();
        } catch (RuntimeException | LinkageError failure) {
            fail("begin", failure);
        }
    }

    private void buildFaultSteps() {
        steps.add(() -> coordinator.eventually("initial exact dependency and provider composition", Duration.ofSeconds(45), () -> {
            service = plugin.getServer().getServicesManager().load(MaddPrestigeService.class);
            economyController = plugin.getServer().getPluginManager().getPlugin("Phase8E-Economy");
            economy = plugin.getServer().getServicesManager().load(Economy.class);
            return service != null && providerVisible(ALPHA) && providerVisible(BETA)
                    && economyController != null && economyController.isEnabled() && economy != null
                    && "Phase8EControlledEconomy".equals(economy.getName())
                    && VAULT_PROVIDERS.stream().allMatch(this::providerVisible)
                    && providerVisible(MCMO_PROVIDER)
                    && providerVisible(GRIEF_PREVENTION_CLAIMS_PROVIDER)
                    && providerVisible(WORLD_GUARD_REGION_PROVIDER)
                    && providerVisible(CRAFT_ENGINE_ITEM_COUNT_PROVIDER)
                    && providerVisible(CRAFT_ENGINE_ITEM_REWARD_PROVIDER);
        }, () -> {
            assertExactDependencies();
            pass("safely dormant first boot discovered exact setup providers without publishing progression");
            coordinator.advance();
        }));
        addSetupStart("fault-profile canonical setup session started");
        addSetupActions(
                setupAction(() -> SETUP_PROVIDER + setupSession + INTERNAL_PROVIDER_SELECTION,
                        "internal rank authority selected"),
                setupAction(() -> SETUP_STAGE + setupSession + BASE_STAGE_ARGUMENTS, "base stage added"),
                setupAction(() -> SETUP_STAGE + setupSession + " alpha Alpha", "Alpha-qualified stage added"),
                setupAction(() -> SETUP_STAGE + setupSession + " target Target", "Beta-qualified target stage added"),
                setupAction(() -> SETUP_BASELINE + setupSession + BASE_STAGE_ID, "base stage selected"),
                setupAction(() -> SETUP_REQUIREMENT + setupSession
                        + " alpha alpha_points " + ALPHA + " points GREATER_OR_EQUAL 1 ABSOLUTE LIVE",
                        "Alpha requirement attached"),
                setupAction(() -> SETUP_REQUIREMENT + setupSession
                        + " target beta_tokens " + BETA + " tokens GREATER_OR_EQUAL 1 ABSOLUTE LIVE",
                        "Beta requirement attached"),
                setupAction(() -> "maddprestige setup cost " + setupSession
                        + " fee vault_economy_cost vault_economy CURRENCY_AMOUNT 1 Fee",
                        "controlled Vault cost attached during dormant setup discovery"),
                setupAction(() -> SETUP_PRESTIGE + setupSession + " enabled target base",
                        "Prestige transition attached"));
        addSetupPublication("consequential fault-profile setup preview compiled",
                "setup risk acknowledged with server-issued token", "phase8e fault profile",
                "consequential fault profile applied as ");
        steps.add(() -> enableFaultIntegrations(coordinator::advance));
        steps.add(() -> coordinator.eventually("final fault profile and dependency composition", Duration.ofSeconds(60), () ->
                VAULT_PROVIDERS.stream().allMatch(this::providerVisible)
                        && providerVisible(MCMO_PROVIDER)
                        && providerVisible(GRIEF_PREVENTION_CLAIMS_PROVIDER)
                        && providerVisible(WORLD_GUARD_REGION_PROVIDER)
                        && providerVisible(CRAFT_ENGINE_ITEM_COUNT_PROVIDER)
                        && providerVisible(CRAFT_ENGINE_ITEM_REWARD_PROVIDER)
                        && service.stages().successful()
                        && service.stages().value().orElseThrow().size() == 3, () -> {
            pass("canonical fault profile kept Vault consequential and enabled every exact dependency integration");
            coordinator.advance();
        }));
        steps.add(this::normalRankAndPrestige);
        steps.add(this::cancelUnknownRank);
        steps.add(this::throwingPreListener);
        steps.add(this::recursiveAndCrossPlayerListener);
        steps.add(this::rebindProviderDuringPre);
        steps.add(this::loseEconomyDuringPre);
        steps.add(this::definitelyNotApplied);
        steps.add(this::throwingEconomyCallback);
        steps.add(this::appliedAndVerified);
        steps.add(this::uncertainAndReconciliationRequired);
        steps.add(this::throwingPostListener);
        steps.add(() -> exerciseAlphaMode("UNAVAILABLE", "provider unavailable"));
        steps.add(() -> exerciseAlphaMode("THROW", "runtime callback throw"));
        steps.add(() -> exerciseAlphaMode("LINKAGE", "linkage-like callback failure"));
        steps.add(() -> exerciseAlphaMode("MISSING", "incomplete output"));
        steps.add(() -> exerciseAlphaMode("EXTRA", "unexpected extra output"));
        steps.add(() -> exerciseAlphaMode("NULL", "null output"));
        steps.add(() -> exerciseAlphaMode("WRONG_TYPE", "wrong metric type"));
        steps.add(this::exerciseHungAlpha);
        steps.add(this::exerciseSynchronouslyBlockedAlpha);
        steps.add(this::exerciseBetaHealthIsolation);
        steps.add(this::exerciseDuplicateRegistration);
        steps.add(this::exerciseUnregisterRebind);
        steps.add(() -> dependencyCycle("mcMMO", Set.of(MCMO_PROVIDER), false));
        steps.add(() -> dependencyCycle("GriefPrevention",
                Set.of(GRIEF_PREVENTION_CLAIMS_PROVIDER, GRIEF_PREVENTION_REWARD_PROVIDER), false));
        steps.add(() -> dependencyCycle("WorldGuard", Set.of(WORLD_GUARD_REGION_PROVIDER), false));
        steps.add(this::craftEngineReloadCycle);
        steps.add(() -> dependencyCycle("PlaceholderAPI", Set.of(), false));
        steps.add(() -> compatibilityDependencyCycle("EconomyShopGUI",
                "me.gypopo.economyshopgui.api.events.PostTransactionEvent"));
        steps.add(() -> compatibilityDependencyCycle("QuickShop-Hikari",
                "com.ghostchu.quickshop.api.event.economy.ShopSuccessPurchaseEvent"));
        steps.add(() -> dependencyCycle("Vault", VAULT_PROVIDERS, true));
        steps.add(this::finishFault);
    }

    private void buildAbsenceSteps() {
        steps.add(() -> coordinator.eventually("absence provider discovery", () -> {
            service = plugin.getServer().getServicesManager().load(MaddPrestigeService.class);
            return service != null && providerVisible(ALPHA) && providerVisible(BETA);
        }, () -> {
            for (String pluginName : EXACT_DEPENDENCIES.keySet()) {
                require(plugin.getServer().getPluginManager().getPlugin(pluginName) == null,
                        pluginName + " was unexpectedly installed in absence environment");
            }
            Set<String> forbidden = Set.of("vault_balance", "vault_economy_cost", "vault_economy_reward",
                    MCMO_PROVIDER, GRIEF_PREVENTION_CLAIMS_PROVIDER, GRIEF_PREVENTION_REWARD_PROVIDER,
                    WORLD_GUARD_REGION_PROVIDER, CRAFT_ENGINE_ITEM_COUNT_PROVIDER, CRAFT_ENGINE_ITEM_REWARD_PROVIDER);
            require(forbidden.stream().noneMatch(this::providerVisible),
                    "absent dependency published a provider");
            pass("all optional dependencies were absent at boot and published no false capability");
            coordinator.advance();
        }));
        addSetupStart("absence canonical setup session started");
        addSetupActions(
                setupAction(() -> SETUP_PROVIDER + setupSession + INTERNAL_PROVIDER_SELECTION,
                        "absence internal rank authority selected"),
                setupAction(() -> SETUP_STAGE + setupSession + BASE_STAGE_ARGUMENTS, "absence base stage added"),
                setupAction(() -> SETUP_STAGE + setupSession + " alpha Alpha", "absence Alpha-qualified stage added"),
                setupAction(() -> SETUP_STAGE + setupSession + " target Target",
                        "absence Beta-qualified target stage added"),
                setupAction(() -> SETUP_BASELINE + setupSession + BASE_STAGE_ID, "absence base stage selected"),
                setupAction(() -> SETUP_REQUIREMENT + setupSession
                        + " alpha alpha_points " + ALPHA + " points GREATER_OR_EQUAL 1 ABSOLUTE LIVE",
                        "absence Alpha requirement attached"),
                setupAction(() -> SETUP_REQUIREMENT + setupSession
                        + " target beta_tokens " + BETA + " tokens GREATER_OR_EQUAL 1 ABSOLUTE LIVE",
                        "absence Beta requirement attached"),
                setupAction(() -> SETUP_PRESTIGE + setupSession + " disabled", "absence Prestige disabled"));
        addSetupPublication("absence profile preview compiled without optional dependencies",
                "absence setup risk acknowledged with server-issued token", "phase8e absence profile",
                "absence profile applied as ");
        steps.add(() -> coordinator.eventually("absence active composition", () -> service.stages().successful()
                && service.stages().value().orElseThrow().size() == 3, () -> {
            pass("absence environment published the configured external-provider profile");
            coordinator.advance();
        }));
        steps.add(() -> coordinator.await("absence unrelated evaluation", service.evaluateRankUp(UUID.randomUUID()), result -> {
            require(result.successful() && result.value().orElseThrow().status()
                    == OperationEvaluationStatus.ELIGIBLE,
                    "absent optional dependencies poisoned unrelated external-provider evaluation");
            pass("unrelated configured progression remained usable with all optional dependencies absent");
            finish("PHASE8E-ABSENCE COMPLETE pass-count=" + passed);
        }));
    }

    private void buildSetupAuditSteps() {
        steps.add(() -> coordinator.eventually("setup-audit exact dependency discovery", Duration.ofSeconds(45), () -> {
            service = plugin.getServer().getServicesManager().load(MaddPrestigeService.class);
            economyController = plugin.getServer().getPluginManager().getPlugin("Phase8E-Economy");
            economy = plugin.getServer().getServicesManager().load(Economy.class);
            return service != null && economyController != null && economyController.isEnabled() && economy != null
                    && VAULT_PROVIDERS.stream().allMatch(this::providerVisible)
                    && providerVisible(MCMO_PROVIDER) && providerVisible(MANUAL_PROGRESS_PROVIDER)
                    && providerVisible(GRIEF_PREVENTION_CLAIMS_PROVIDER)
                    && providerVisible(GRIEF_PREVENTION_REWARD_PROVIDER)
                    && providerVisible(WORLD_GUARD_REGION_PROVIDER) && providerVisible(CRAFT_ENGINE_ITEM_COUNT_PROVIDER)
                    && providerVisible(CRAFT_ENGINE_ITEM_REWARD_PROVIDER);
        }, () -> {
            assertExactDependencies();
            pass("setup discovery exposed every exact built-in integration candidate");
            coordinator.advance();
        }));
        steps.add(() -> command("maddprestige setup discover", lines -> {
            require(lines.stream().anyMatch(line -> line.contains(MANUAL_PROGRESS_PROVIDER)
                    && line.contains("mcmmo_adjusted_xp_total")),
                    "setup discovery omitted the mcMMO-backed manual metric");
            pass("setup discovery advertised phase5_events:mcmmo_adjusted_xp_total");
            coordinator.advance();
        }));
        addSetupStart("setup-audit canonical session started");
        addSetupActions(
                setupAction(() -> SETUP_PROVIDER + setupSession + INTERNAL_PROVIDER_SELECTION,
                        "setup-audit internal rank authority selected"),
                setupAction(() -> SETUP_STAGE + setupSession + BASE_STAGE_ARGUMENTS, "setup-audit base stage added"),
                setupAction(() -> SETUP_STAGE + setupSession + " manual Manual",
                        "setup-audit manual mcMMO event stage added"),
                setupAction(() -> SETUP_STAGE + setupSession + " vault Vault", "setup-audit Vault stage added"),
                setupAction(() -> SETUP_STAGE + setupSession + " mcmmo McMMO", "setup-audit mcMMO stage added"),
                setupAction(() -> SETUP_STAGE + setupSession + " claims Claims",
                        "setup-audit GriefPrevention stage added"),
                setupAction(() -> SETUP_BASELINE + setupSession + BASE_STAGE_ID, "setup-audit baseline selected"));
        steps.add(() -> commandFails(SETUP_REQUIREMENT + setupSession
                + " vault wg worldguard_region inside_region EQUAL true ABSOLUTE LIVE",
                "WorldGuard region-id requirement", coordinator::advance));
        steps.add(() -> commandFails(SETUP_REQUIREMENT + setupSession
                + " vault ce craftengine_item_count item_count GREATER_OR_EQUAL 1 ABSOLUTE LIVE",
                "CraftEngine item-id requirement", coordinator::advance));
        steps.add(() -> commandFails(SETUP_REQUIREMENT + setupSession
                + " vault papi placeholder_input sample GREATER_OR_EQUAL 1 ABSOLUTE LIVE",
                "Placeholder input binding", coordinator::advance));
        steps.add(() -> commandFails("maddprestige setup reward " + setupSession
                + " ce_gift craftengine_item_reward custom_item COUNT 1 Gift",
                "CraftEngine item-id reward", coordinator::advance));
        addSetupActions(
                setupAction(() -> SETUP_REQUIREMENT + setupSession
                        + " manual manual_xp phase5_events mcmmo_adjusted_xp_total GREATER_OR_EQUAL 0 ABSOLUTE LIVE",
                        "mcMMO-backed manual progress requirement selected"),
                setupAction(() -> SETUP_REQUIREMENT + setupSession
                        + " vault vault_balance vault_balance balance GREATER_OR_EQUAL 0 ABSOLUTE LIVE",
                        "Vault balance requirement selected"),
                setupAction(() -> SETUP_REQUIREMENT + setupSession
                        + " mcmmo mcmmo_power mcmmo power_level GREATER_OR_EQUAL 0 ABSOLUTE LIVE",
                        "mcMMO power-level requirement selected"),
                setupAction(() -> SETUP_REQUIREMENT + setupSession
                        + " claims gp_claims griefprevention_claims owned_claim_count GREATER_OR_EQUAL 0 ABSOLUTE LIVE",
                        "GriefPrevention claim requirement selected"),
                setupAction(() -> "maddprestige setup cost " + setupSession
                        + " fee vault_economy_cost vault_economy CURRENCY_AMOUNT 1 Fee", "Vault cost selected"),
                setupAction(() -> "maddprestige setup reward " + setupSession
                        + " claim_blocks griefprevention_claim_blocks_reward bonus_claim_blocks COUNT 1 ClaimBlocks",
                        "GriefPrevention reward selected"),
                setupAction(() -> SETUP_PRESTIGE + setupSession + " disabled", "setup-audit Prestige disabled"));
        addSetupPublication("setup-audit generated configuration compiled", "setup-audit risk acknowledged",
                "phase8e setup audit", "setup-audit configuration published as ");
        steps.add(() -> coordinator.eventually("setup-audit post-apply integration reconciliation", Duration.ofSeconds(60), () ->
                VAULT_PROVIDERS.stream().allMatch(this::providerVisible)
                        && providerVisible(MCMO_PROVIDER)
                        && providerVisible(MANUAL_PROGRESS_PROVIDER)
                        && providerVisible(GRIEF_PREVENTION_CLAIMS_PROVIDER)
                        && providerVisible(GRIEF_PREVENTION_REWARD_PROVIDER)
                        && !providerVisible(WORLD_GUARD_REGION_PROVIDER)
                        && !providerVisible(CRAFT_ENGINE_ITEM_COUNT_PROVIDER)
                        && !providerVisible(CRAFT_ENGINE_ITEM_REWARD_PROVIDER)
                        && providerVisible(ALPHA) && providerVisible(BETA)
                        && service.stages().successful() && service.stages().value().orElseThrow().size() == 5,
                () -> {
                    UUID player = fundedPlayer();
                    coordinator.await("setup-audit active configuration usability", service.rankUp(player), result -> {
                        completed(result, "setup-audit active configuration usability");
                        require(balance(player) == 99D, "generated Vault cost did not apply exactly once");
                        coordinator.await("setup-audit public progress read", service.playerProgress(player), progress -> {
                            require(progress.successful(), "published setup profile is not publicly readable");
                            require(progress.value().orElseThrow().stage().orElseThrow().value().equals("manual"),
                                    "manual mcMMO-backed stage was not reached");
                            pass("phase5_events:mcmmo_adjusted_xp_total remained usable after generated mcMMO "
                                    + "integration state survived apply/reconciliation; the active profile completed "
                                    + "a real transition and unrelated integrations remained disabled");
                            coordinator.advance();
                        });
                    });
                }));
        steps.add(() -> {
            pass("setup provider/integration persistence audit completed without publishing an incomplete choice");
            finish("PHASE8E-SETUP-AUDIT COMPLETE pass-count=" + passed);
        });
    }

    private void normalRankAndPrestige() {
        UUID player = fundedPlayer();
        coordinator.await("normal Alpha rank", service.rankUp(player), first -> {
            OperationResult alpha = completed(first, "normal Alpha rank");
            assertEventPair(player, alpha, preRank, postRank, OperationStatus.COMPLETED);
            coordinator.await("normal Beta rank", service.rankUp(player), second -> {
                OperationResult beta = completed(second, "normal Beta rank");
                assertEventPair(player, beta, preRank, postRank, OperationStatus.COMPLETED);
                coordinator.await("normal Prestige", service.prestige(player), third -> {
                    OperationResult prestige = completed(third, "normal Prestige");
                    assertEventPair(player, prestige, prePrestige, postPrestige, OperationStatus.COMPLETED);
                    coordinator.await("normal terminal progress", service.playerProgress(player), progress -> {
                        PlayerProgressSnapshot snapshot = value(progress, "normal terminal progress");
                        require(snapshot.stage().orElseThrow().value().equals("base")
                                && snapshot.currentPrestige() == 1 && snapshot.lifetimePrestige() == 1,
                                "normal rank/Prestige state is incoherent: " + snapshot);
                        require(balance(player) == 98D, "two configured stage-transition Vault costs were not exact");
                        pass("normal RankUp/Prestige PRE and durable POST ordering, identities and status are exact");
                        coordinator.advance();
                    });
                });
            });
        });
    }

    private void cancelUnknownRank() {
        cancelRankPlayer = fundedPlayer();
        long rowsBefore = playerRows(cancelRankPlayer);
        coordinator.await("unknown-player PRE cancellation", service.rankUp(cancelRankPlayer), result -> {
            OperationResult operation = value(result, "unknown-player PRE cancellation");
            require(operation.status() == OperationStatus.BLOCKED && operation.durableOperationId().isEmpty(),
                    "PRE cancellation claimed durability: " + operation);
            require(preRank.containsKey(cancelRankPlayer) && !postRank.containsKey(cancelRankPlayer),
                    "PRE cancellation event ordering is wrong");
            require(playerRows(cancelRankPlayer) == rowsBefore,
                    "PRE cancellation initialized durable player state");
            require(balance(cancelRankPlayer) == 100D, "PRE cancellation consumed Vault balance");
            pass("unknown-player PRE cancellation retained request identity with zero durable/economic effect");
            coordinator.advance();
        });
    }

    private void throwingPreListener() {
        throwPreRankPlayer = fundedPlayer();
        coordinator.await("throwing PRE listener", service.rankUp(throwPreRankPlayer), result -> {
            OperationResult operation = value(result, "throwing PRE listener");
            require(operation.status() == OperationStatus.BLOCKED && operation.durableOperationId().isEmpty(),
                    "throwing PRE listener did not fail closed before durability: " + operation);
            require(preRank.containsKey(throwPreRankPlayer) && !postRank.containsKey(throwPreRankPlayer),
                    "throwing PRE listener emitted an invalid event sequence");
            require(playerRows(throwPreRankPlayer) == 0 && balance(throwPreRankPlayer) == 100D,
                    "throwing PRE listener caused a durable or economic side effect");
            pass("throwing PRE subscriber failed closed before durability with zero economic effect");
            coordinator.advance();
        });
    }

    private void recursiveAndCrossPlayerListener() {
        recursivePlayer = fundedPlayer();
        crossPlayer = fundedPlayer();
        coordinator.await("reentrant parent rank", service.rankUp(recursivePlayer), parent -> {
            completed(parent, "reentrant parent rank");
            coordinator.eventually("nested listener operations", () -> recursiveRank != null && recursivePrestige != null
                    && crossPlayerRank != null
                    && recursiveRank.toCompletableFuture().isDone()
                    && recursivePrestige.toCompletableFuture().isDone()
                    && crossPlayerRank.toCompletableFuture().isDone(), () -> {
                OperationResult rank = value(recursiveRank.toCompletableFuture().join(), "same-player rank recurse");
                OperationResult prestige = value(recursivePrestige.toCompletableFuture().join(),
                        "same-player Prestige recurse");
                OperationResult cross = value(crossPlayerRank.toCompletableFuture().join(), "cross-player rank");
                require(rank.status() == OperationStatus.CONFLICT && prestige.status() == OperationStatus.CONFLICT,
                        "same-player recursion did not conflict");
                require(cross.status() == OperationStatus.COMPLETED,
                        "allowed cross-player listener operation did not complete");
                pass("same-player RankUp/Prestige recursion conflicted while cross-player operation completed");
                coordinator.advance();
            });
        });
    }

    private void rebindProviderDuringPre() {
        rebindInPrePlayer = fundedPlayer();
        coordinator.await("provider rebind after PRE", service.rankUp(rebindInPrePlayer), result -> {
            OperationResult operation = value(result, "provider rebind after PRE");
            require(operation.status() != OperationStatus.COMPLETED && balance(rebindInPrePlayer) == 100D,
                    "stale provider generation authorized a clean consequential execution");
            coordinator.eventually("provider rebind recovery", () -> providerVisible(ALPHA), () -> {
                pass("provider generation replacement after PRE failed stale work closed and recovered");
                coordinator.advance();
            });
        });
    }

    private void loseEconomyDuringPre() {
        loseEconomyInPrePlayer = fundedPlayer();
        coordinator.await("Vault service loss after PRE", service.rankUp(loseEconomyInPrePlayer), result -> {
            require(!result.successful() || result.value().orElseThrow().status() != OperationStatus.COMPLETED,
                    "Vault loss after authorization was presented as clean success");
            require(balance(loseEconomyInPrePlayer) == 100D,
                    "Vault service loss consumed a cost");
            registerEconomy();
            coordinator.eventually("Vault replacement recovery", Duration.ofSeconds(30), () ->
                    VAULT_PROVIDERS.stream().allMatch(this::providerVisible), () -> {
                pass("Economy service loss after PRE failed closed; replacement rebound exact Vault providers");
                coordinator.advance();
            });
        });
    }

    private void definitelyNotApplied() {
        setEconomyMode("FAIL");
        UUID player = fundedPlayer();
        coordinator.await("definitely-not-applied Vault response", service.rankUp(player), result -> {
            OperationResult operation = value(result, "definitely-not-applied Vault response");
            require(operation.status() == OperationStatus.FAILED && balance(player) == 100D,
                    "definitely-not-applied action did not fail cleanly: " + operation);
            assertPostStatus(player, operation, OperationStatus.FAILED);
            setEconomyMode(SUCCESS_MODE);
            pass("definitely-not-applied external action produced FAILED with no balance or stage success claim");
            coordinator.advance();
        });
    }

    private void appliedAndVerified() {
        UUID player = fundedPlayer();
        coordinator.await("applied-and-verified Vault response", service.rankUp(player), result -> {
            OperationResult operation = completed(result, "applied-and-verified Vault response");
            require(balance(player) == 99D, "verified Vault response did not apply exactly once");
            assertPostStatus(player, operation, OperationStatus.COMPLETED);
            pass("applied and verified external action produced one durable clean success");
            coordinator.advance();
        });
    }

    private void throwingEconomyCallback() {
        setEconomyMode("THROW");
        UUID player = fundedPlayer();
        coordinator.await("throwing Vault callback", service.rankUp(player), result -> {
            require(!result.successful() || result.value().orElseThrow().status() != OperationStatus.COMPLETED,
                    "throwing Vault callback was presented as clean success");
            require(balance(player) == 100D, "throwing Vault callback changed the controlled balance");
            setEconomyMode(SUCCESS_MODE);
            pass("throwing external action callback failed closed without a balance or stage success claim");
            coordinator.advance();
        });
    }

    private void uncertainAndReconciliationRequired() {
        setEconomyMode("UNCERTAIN");
        UUID player = fundedPlayer();
        coordinator.await("uncertain Vault response", service.rankUp(player), result -> {
            OperationResult operation = value(result, "uncertain Vault response");
            require(operation.status() == OperationStatus.NEEDS_RECONCILIATION
                    && operation.durableOperationId().isPresent(),
                    "uncertain external action was not reconciliation-required: " + operation);
            require(balance(player) == 99D, "uncertain action did not preserve its real applied observation");
            assertPostStatus(player, operation, OperationStatus.NEEDS_RECONCILIATION);
            setEconomyMode(SUCCESS_MODE);
            pass("uncertain external action retained durable identity and was never presented as clean success");
            coordinator.advance();
        });
    }

    private void throwingPostListener() {
        throwPostRankPlayer = fundedPlayer();
        int before = withdrawals();
        coordinator.await("throwing POST listener", service.rankUp(throwPostRankPlayer), result -> {
            OperationResult operation = completed(result, "throwing POST listener");
            require(postRank.containsKey(throwPostRankPlayer) && balance(throwPostRankPlayer) == 99D
                    && withdrawals() == before + 1,
                    "POST listener failure changed or duplicated the durable result");
            assertPostStatus(throwPostRankPlayer, operation, OperationStatus.COMPLETED);
            pass("POST listener failure could not roll back or duplicate the already durable operation");
            coordinator.advance();
        });
    }

    private void exerciseAlphaMode(String faultMode, String label) {
        providerCommand(ALPHA_COMMAND, PROVIDER_MODE_COMMAND, faultMode);
        UUID player = UUID.randomUUID();
        coordinator.await(ALPHA_LABEL + label, service.evaluateRankUp(player), result -> {
            require(!result.successful() || result.value().orElseThrow().status()
                    != OperationEvaluationStatus.ELIGIBLE,
                    ALPHA_LABEL + label + " became eligible");
            require(providerVisible(BETA), ALPHA_LABEL + label + " poisoned Beta registration");
            providerCommand(ALPHA_COMMAND, PROVIDER_MODE_COMMAND, HEALTHY_MODE);
            providerCommand(ALPHA_COMMAND, REBIND_COMMAND);
            eventuallyEligible(player, () -> {
                pass(ALPHA_LABEL + label
                        + " failed closed and a new generation recovered while Beta remained usable");
                coordinator.advance();
            });
        });
    }

    private void exerciseHungAlpha() {
        providerCommand(ALPHA_COMMAND, PROVIDER_MODE_COMMAND, "HANG");
        int cancellationsBefore = alphaCancellations();
        UUID player = UUID.randomUUID();
        long started = System.nanoTime();
        coordinator.await("Alpha deadline", service.evaluateRankUp(player), result -> {
            long millis = Duration.ofNanos(System.nanoTime() - started).toMillis();
            measuredMillis.put("alpha-hang", millis);
            require(!result.successful() || result.value().orElseThrow().status()
                    != OperationEvaluationStatus.ELIGIBLE,
                    "hung Alpha callback became eligible");
            require(millis < 30_000L, "hung Alpha callback exceeded bounded qualification window: " + millis);
            require(alphaCancellations() > cancellationsBefore,
                    "hung Alpha callback did not observe deadline cancellation");
            providerCommand(ALPHA_COMMAND, PROVIDER_MODE_COMMAND, HEALTHY_MODE);
            providerCommand(ALPHA_COMMAND, REBIND_COMMAND);
            eventuallyEligible(player, () -> {
                pass("hung Alpha callback observed the production deadline/cancellation and recovered in "
                        + millis + " ms");
                coordinator.advance();
            });
        });
    }

    private void exerciseSynchronouslyBlockedAlpha() {
        UUID betaPlayer = fundedPlayer();
        coordinator.await("synchronous-block Beta prerequisite", service.rankUp(betaPlayer), prerequisite -> {
            completed(prerequisite, "synchronous-block Beta prerequisite");
            providerCommand(ALPHA_COMMAND, "sync-reset");
            providerCommand(ALPHA_COMMAND, PROVIDER_MODE_COMMAND, "SYNC_BLOCK");
            AlphaAttemptBatch oldGeneration = submitAlphaAttempts(4);
            coordinator.eventually("synchronous Alpha callback saturation", Duration.ofSeconds(10), () ->
                    alphaObservation(OBSERVED_SYNCHRONOUS_ACTIVE) == 4
                            && alphaObservation("observedSynchronousHighWater") == 4, () -> {
                int generationOne = alphaObservation(OBSERVED_REGISTRATIONS);
                providerCommand(ALPHA_COMMAND, REBIND_COMMAND);
                coordinator.eventually("Alpha generation 2 registration during generation 1 block", () ->
                        alphaObservation(OBSERVED_REGISTRATIONS) > generationOne && providerVisible(ALPHA), () -> {
                    rejectedAlphaBatch("Alpha generation 2 overlap", 8, generationTwo -> {
                        betaProbe(betaPlayer, "generation 2 overlap", betaOne -> {
                            repeatAlphaRebinds(3, () -> {
                                rejectedAlphaBatch("rapid repeated Alpha rebind overlap", 4, repeated -> {
                                    betaProbe(betaPlayer, "rapid repeated rebind overlap", betaTwo ->
                                            unregisterBlockedAlpha(betaPlayer, oldGeneration, generationTwo,
                                                    repeated, betaOne, betaTwo));
                                });
                            });
                        });
                    });
                });
            });
        });
    }

    private void unregisterBlockedAlpha(
            UUID betaPlayer,
            AlphaAttemptBatch oldGeneration,
            AlphaAttemptResult generationTwo,
            AlphaAttemptResult repeated,
            long betaOne,
            long betaTwo) {
        providerCommand(ALPHA_COMMAND, "unregister");
        coordinator.eventually("Alpha unregister without immediate rebind while generation 1 remains blocked", () ->
                !providerVisible(ALPHA), () -> {
            require(alphaObservation(OBSERVED_SYNCHRONOUS_ACTIVE) == 4,
                    "unregister reclaimed the logical budget while old callbacks remained blocked");
            require(callbackWorkerCount() <= 8,
                    "unregistered Alpha overlap exceeded the shared callback executor bound");
            int before = alphaObservation(OBSERVED_REGISTRATIONS);
            providerCommand(ALPHA_COMMAND, REBIND_COMMAND);
            coordinator.eventually("Alpha rebind after bounded unregistered interval", () ->
                    alphaObservation(OBSERVED_REGISTRATIONS) > before && providerVisible(ALPHA), () ->
                    rejectedAlphaBatch("post-unregister Alpha overlap", 4, afterUnregister ->
                            betaProbe(betaPlayer, "post-unregister rebind overlap", betaThree ->
                                    releaseBlockedAlpha(new BlockedAlphaRelease(betaPlayer, oldGeneration,
                                            generationTwo, repeated, afterUnregister,
                                            new BetaLatencies(betaOne, betaTwo, betaThree))))));
        });
    }

    private void releaseBlockedAlpha(BlockedAlphaRelease release) {
        UUID betaPlayer = release.betaPlayer();
        AlphaAttemptBatch oldGeneration = release.oldGeneration();
        AlphaAttemptResult generationTwo = release.generationTwo();
        AlphaAttemptResult repeated = release.repeated();
        AlphaAttemptResult afterUnregister = release.afterUnregister();
        long betaOne = release.betaLatencies().first();
        long betaTwo = release.betaLatencies().second();
        long betaThree = release.betaLatencies().third();
        coordinator.eventually("old Alpha generation deadline completion", Duration.ofSeconds(10), () ->
                oldGeneration.terminal.get() == oldGeneration.attempted, () -> {
            require(oldGeneration.falseSuccess.get() == 0,
                    "stale generation 1 work produced a clean result after replacement");
            require(alphaObservation(OBSERVED_SYNCHRONOUS_ACTIVE) == 4
                            && alphaObservation("observedSynchronousCompleted") == 0,
                    "generation 1 callbacks escaped before explicit release");
            long releaseStarted = System.nanoTime();
            providerCommand(ALPHA_COMMAND, "sync-release");
            coordinator.eventually("cross-generation Alpha callback release", Duration.ofSeconds(10), () ->
                    alphaObservation(OBSERVED_SYNCHRONOUS_ACTIVE) == 0
                            && alphaObservation("observedSynchronousCompleted") == 4, () -> {
                long recoveryMillis = Duration.ofNanos(System.nanoTime() - releaseStarted).toMillis();
                providerCommand(ALPHA_COMMAND, PROVIDER_MODE_COMMAND, HEALTHY_MODE);
                providerCommand(ALPHA_COMMAND, REBIND_COMMAND);
                eventuallyEligible(UUID.randomUUID(), () -> betaProbe(betaPlayer, "post-release recovery", betaFour -> {
                    int callbackWorkers = callbackWorkerCount();
                    require(callbackWorkers <= 8, "shared callback executor exceeded eight workers: "
                            + callbackWorkers);
                    require(synchronouslyBlockedCallbackWorkerCount() == 0,
                            "a released Alpha callback worker remains blocked");
                    require(alphaObservation(OBSERVED_SYNCHRONOUS_ACTIVE) == 0,
                            "logical Alpha budget did not return to zero active callbacks");
                    long maximumBeta = java.util.stream.LongStream.of(betaOne, betaTwo, betaThree, betaFour)
                            .max().orElseThrow();
                    measuredMillis.put("sync-rebind-beta-max", maximumBeta);
                    measuredMillis.put("sync-rebind-recovery", recoveryMillis);
                    pass("callback executor=8 logical-alpha-budget=4 old-generation-blocked=4; generation-2 "
                            + generationTwo + "; repeated-rebind " + repeated + "; post-unregister "
                            + afterUnregister + "; Beta remained usable across overlap with max-latency="
                            + maximumBeta + " ms; recovery=" + recoveryMillis + " ms; final-active=0 "
                            + "callback-workers=" + callbackWorkers);
                    coordinator.advance();
                }));
            });
        });
    }

    private AlphaAttemptBatch submitAlphaAttempts(int calls) {
        AlphaAttemptBatch batch = new AlphaAttemptBatch(calls, alphaObservation("observedReads"));
        for (int index = 0; index < calls; index++) {
            service.evaluateRankUp(UUID.randomUUID()).whenComplete((result, failure) -> {
                if (failure == null && result != null && result.successful()
                        && result.value().orElseThrow().status() == OperationEvaluationStatus.ELIGIBLE) {
                    batch.falseSuccess.incrementAndGet();
                }
                batch.terminal.incrementAndGet();
            });
        }
        return batch;
    }

    private void rejectedAlphaBatch(String label, int calls, Consumer<AlphaAttemptResult> continuation) {
        AlphaAttemptBatch batch = submitAlphaAttempts(calls);
        coordinator.eventually(label + " terminal results", Duration.ofSeconds(10), () -> batch.terminal.get() == calls, () -> {
            int admitted = alphaObservation("observedReads") - batch.readsBefore;
            AlphaAttemptResult result = new AlphaAttemptResult(calls, admitted, calls - admitted);
            require(batch.falseSuccess.get() == 0, label + " produced false eligibility");
            require(admitted == 0, label + " received a fresh callback budget: " + result);
            require(alphaObservation(OBSERVED_SYNCHRONOUS_ACTIVE) == 4,
                    label + " changed the four blocked old-generation callbacks");
            continuation.accept(result);
        });
    }

    private void repeatAlphaRebinds(int remaining, Runnable continuation) {
        if (remaining == 0) {
            continuation.run();
            return;
        }
        int before = alphaObservation(OBSERVED_REGISTRATIONS);
        providerCommand(ALPHA_COMMAND, REBIND_COMMAND);
        coordinator.eventually("rapid Alpha rebind " + remaining, () ->
                alphaObservation(OBSERVED_REGISTRATIONS) > before && providerVisible(ALPHA), () ->
                repeatAlphaRebinds(remaining - 1, continuation));
    }

    private void betaProbe(UUID betaPlayer, String label, LongConsumer continuation) {
        long started = System.nanoTime();
        coordinator.await("Beta isolation during " + label, service.evaluateRankUp(betaPlayer), beta -> {
            long millis = Duration.ofNanos(System.nanoTime() - started).toMillis();
            require(beta.successful() && beta.value().orElseThrow().status() == OperationEvaluationStatus.ELIGIBLE,
                    "Alpha pressure blocked Beta during " + label);
            require(millis < 3_000L, "Beta consumed the Alpha deadline budget during " + label + ": " + millis);
            continuation.accept(millis);
        });
    }

    private static int callbackWorkerCount() {
        return (int) Thread.getAllStackTraces().keySet().stream().filter(Thread::isAlive)
                .filter(thread -> thread.getName().startsWith("maddprestige-provider-callback-")).count();
    }

    private static int synchronouslyBlockedCallbackWorkerCount() {
        return (int) Thread.getAllStackTraces().entrySet().stream()
                .filter(entry -> entry.getKey().isAlive()
                        && entry.getKey().getName().startsWith("maddprestige-provider-callback-"))
                .filter(entry -> Arrays.stream(entry.getValue()).anyMatch(frame ->
                        frame.getClassName().equals(
                                "net.maddkraft.qualification.phase8e.alpha.Phase8EAlphaProvider")
                                && frame.getMethodName().equals("synchronouslyBlock")))
                .count();
    }

    private static final class AlphaAttemptBatch {
        private final int attempted;
        private final int readsBefore;
        private final AtomicInteger terminal = new AtomicInteger();
        private final AtomicInteger falseSuccess = new AtomicInteger();

        private AlphaAttemptBatch(int attempted, int readsBefore) {
            this.attempted = attempted;
            this.readsBefore = readsBefore;
        }
    }

    private record SetupAction(java.util.function.Supplier<String> command, String passMessage) {
    }

    private record AlphaAttemptResult(int attempted, int admitted, int rejected) {
    }

    private record BlockedAlphaRelease(
            UUID betaPlayer,
            AlphaAttemptBatch oldGeneration,
            AlphaAttemptResult generationTwo,
            AlphaAttemptResult repeated,
            AlphaAttemptResult afterUnregister,
            BetaLatencies betaLatencies) {
    }

    private record BetaLatencies(long first, long second, long third) {
    }

    private void exerciseBetaHealthIsolation() {
        UUID player = fundedPlayer();
        coordinator.await("Alpha stage prerequisite", service.rankUp(player), first -> {
            completed(first, "Alpha stage prerequisite");
            require(balance(player) == 99D, "Alpha prerequisite did not apply its exact cost");
            providerCommand(BETA_COMMAND, PROVIDER_MODE_COMMAND, "ONLINE_ONLY");
            coordinator.await("Beta explicit offline limitation", service.evaluateRankUp(player), offline -> {
                require(!offline.successful() || offline.value().orElseThrow().status()
                        != OperationEvaluationStatus.ELIGIBLE,
                        "offline-only Beta limitation became eligible");
                require(balance(player) == 99D && playerRows(player) == 1,
                        "offline Beta evaluation caused an economic or initialization side effect");
                require(providerVisible(ALPHA), "Beta health failure poisoned Alpha");
                providerCommand(BETA_COMMAND, PROVIDER_MODE_COMMAND, "UNAVAILABLE");
                coordinator.await("Beta unavailable", service.evaluateRankUp(player), blocked -> {
                    require(!blocked.successful() || blocked.value().orElseThrow().status()
                            != OperationEvaluationStatus.ELIGIBLE,
                            "unavailable Beta became eligible");
                    require(providerVisible(ALPHA), "Beta health failure poisoned Alpha");
                    providerCommand(BETA_COMMAND, PROVIDER_MODE_COMMAND, HEALTHY_MODE);
                    eventuallyEligible(player, () -> {
                        pass("explicit offline and unhealthy Beta states blocked only their target while Alpha remained usable");
                        coordinator.advance();
                    });
                });
            });
        });
    }

    private void exerciseDuplicateRegistration() {
        providerCommand(ALPHA_COMMAND, "duplicate");
        coordinator.eventually("duplicate provider isolation", () -> providerCount(ALPHA) == 1, () -> {
            UUID player = UUID.randomUUID();
            eventuallyEligible(player, () -> {
                providerCommand(ALPHA_COMMAND, "clear-duplicate");
                pass("duplicate/ambiguous declaration failed safely without replacing the active generation");
                coordinator.advance();
            });
        });
    }

    private void exerciseUnregisterRebind() {
        providerCommand(ALPHA_COMMAND, "unregister");
        coordinator.eventually("Alpha unregister", () -> !providerVisible(ALPHA), () -> {
            require(providerVisible(BETA), "Alpha unregister removed Beta");
            providerCommand(ALPHA_COMMAND, REBIND_COMMAND);
            coordinator.eventually("Alpha rebind", () -> providerVisible(ALPHA), () ->
                    eventuallyEligible(UUID.randomUUID(), () -> {
                        pass("Alpha unregister/rebind replaced its generation without changing Beta or revision");
                        coordinator.advance();
                    }));
        });
    }

    private void dependencyCycle(String name, Set<String> providers, boolean activeDependency) {
        Plugin dependency = plugin.getServer().getPluginManager().getPlugin(name);
        require(dependency != null && dependency.isEnabled(), name + " is not enabled before cycle");
        dispatchMaddPrestigeLifecycle(new PluginDisableEvent(dependency));
        coordinator.eventually(name + " disable/loss signal", () -> providers.stream().allMatch(
                this::providerUnavailableOrAbsent), () -> {
            require(providerVisible(ALPHA) && providerVisible(BETA),
                    name + " disable poisoned external providers");
            UUID candidate = UUID.randomUUID();
            coordinator.await(name + " loss evaluation", service.evaluateRankUp(candidate), result -> {
                if (activeDependency) {
                    require(!result.successful() || result.value().orElseThrow().status()
                            != OperationEvaluationStatus.ELIGIBLE,
                            name + " loss did not block the operation that references it");
                } else {
                    require(result.successful() && result.value().orElseThrow().status()
                            == OperationEvaluationStatus.ELIGIBLE,
                            name + " loss disabled unrelated progression");
                }
                dispatchMaddPrestigeLifecycle(new PluginEnableEvent(dependency));
                coordinator.eventually(name + " re-enable/rebind signal", Duration.ofSeconds(45), () -> dependency.isEnabled()
                        && providers.stream().allMatch(this::providerVisible), () -> {
                    pass(name + " Paper lifecycle loss/return signals were capability-local with correct scope");
                    coordinator.advance();
                });
            });
        });
    }

    private void compatibilityDependencyCycle(String name, String eventClassName) {
        Plugin dependency = plugin.getServer().getPluginManager().getPlugin(name);
        require(dependency != null && dependency.isEnabled(), name + " is not enabled before cycle");
        require(maddPrestigeEventListeners(dependency, eventClassName) == 1,
                name + " compatibility listener was not bound exactly once");
        dispatchMaddPrestigeLifecycle(new PluginDisableEvent(dependency));
        coordinator.eventually(name + " compatibility listener withdrawal", () ->
                maddPrestigeEventListeners(dependency, eventClassName) == 0, () -> {
            coordinator.await(name + " unrelated evaluation during loss", service.evaluateRankUp(UUID.randomUUID()), result -> {
                require(result.successful() && result.value().orElseThrow().status()
                                == OperationEvaluationStatus.ELIGIBLE,
                        name + " compatibility loss disabled unrelated progression");
                dispatchMaddPrestigeLifecycle(new PluginEnableEvent(dependency));
                coordinator.eventually(name + " compatibility listener rebind", Duration.ofSeconds(45), () ->
                        maddPrestigeEventListeners(dependency, eventClassName) == 1, () -> {
                    require(providerVisible(ALPHA) && providerVisible(BETA),
                            name + " lifecycle poisoned external providers");
                    pass(name + " diagnostic-only listener withdrew and rebound exactly once; it exposes no "
                            + "progression callback/reload authority and unrelated progression remained usable");
                    coordinator.advance();
                });
            });
        });
    }

    private int maddPrestigeEventListeners(Plugin dependency, String eventClassName) {
        try {
            Class<?> eventClass = Class.forName(eventClassName, true, dependency.getClass().getClassLoader());
            Object handlers = eventClass.getMethod("getHandlerList").invoke(null);
            RegisteredListener[] listeners = (RegisteredListener[]) handlers.getClass()
                    .getMethod("getRegisteredListeners").invoke(handlers);
            return (int) Arrays.stream(listeners)
                    .filter(listener -> PLUGIN_NAME.equals(listener.getPlugin().getName())).count();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot inspect public event listener boundary for " + eventClassName,
                    failure);
        }
    }

    private void craftEngineReloadCycle() {
        Plugin dependency = plugin.getServer().getPluginManager().getPlugin("CraftEngine");
        require(dependency != null && dependency.isEnabled(), "CraftEngine is not enabled before reload");
        require(providerVisible(CRAFT_ENGINE_ITEM_COUNT_PROVIDER) && providerVisible(CRAFT_ENGINE_ITEM_REWARD_PROVIDER),
                "CraftEngine providers are not usable before reload");
        require(plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
                "craftengine reload config"), "CraftEngine public reload command was unavailable");
        coordinator.eventually("CraftEngine completed reload/rebind signal", Duration.ofSeconds(45), () ->
                providerVisible(CRAFT_ENGINE_ITEM_COUNT_PROVIDER) && providerVisible(CRAFT_ENGINE_ITEM_REWARD_PROVIDER), () -> {
            require(providerVisible(ALPHA) && providerVisible(BETA),
                    "CraftEngine reload poisoned external providers");
            eventuallyEligible(UUID.randomUUID(), () -> {
                pass("CraftEngine public reload and completed reload-event rebind preserved capability scope");
                coordinator.advance();
            });
        });
    }

    private void dispatchMaddPrestigeLifecycle(Event event) {
        int invoked = 0;
        for (RegisteredListener listener : event.getHandlers().getRegisteredListeners()) {
            if (!PLUGIN_NAME.equals(listener.getPlugin().getName())) continue;
            try {
                listener.callEvent(event);
                invoked++;
            } catch (EventException failure) {
                throw new IllegalStateException("MaddPrestige lifecycle listener failed", failure);
            }
        }
        require(invoked > 0, "No MaddPrestige listener accepted " + event.getEventName());
    }

    private void finishFault() {
        require(providerVisible(ALPHA) && providerVisible(BETA), "external providers not healthy at finish");
        pass("fault matrix completed with all unrelated providers available and no false clean success");
        finish("PHASE8E-FAULT COMPLETE pass-count=" + passed + " withdrawals=" + withdrawals()
                + " timings-ms=" + measuredMillis);
    }

    private void registerLifecycleEvents() {
        Listener listener = new Listener() { };
        registerEvent("net.maddkraft.maddprestige.platform.paper.event.PreRankUpEvent", listener, event -> {
            OperationEventSnapshot snapshot = snapshot(event);
            preRank.put(snapshot.playerId(), snapshot);
            UUID player = snapshot.playerId();
            if (player.equals(cancelRankPlayer)) ((Cancellable) event).setCancelled(true);
            if (player.equals(throwPreRankPlayer)) throw new IllegalStateException("controlled PRE listener failure");
            if (player.equals(recursivePlayer)) {
                recursiveRank = service.rankUp(player);
                recursivePrestige = service.prestige(player);
                crossPlayerRank = service.rankUp(crossPlayer);
            }
            if (player.equals(rebindInPrePlayer)) providerCommand(ALPHA_COMMAND, REBIND_COMMAND);
            if (player.equals(loseEconomyInPrePlayer)) unregisterEconomy();
        });
        registerEvent("net.maddkraft.maddprestige.platform.paper.event.PostRankUpEvent", listener, event -> {
            OperationEventSnapshot snapshot = snapshot(event);
            postRank.put(snapshot.playerId(), snapshot);
            if (snapshot.playerId().equals(throwPostRankPlayer)) {
                throw new IllegalStateException("controlled POST listener failure");
            }
        });
        registerEvent("net.maddkraft.maddprestige.platform.paper.event.PrePrestigeEvent", listener,
                event -> prePrestige.put(snapshot(event).playerId(), snapshot(event)));
        registerEvent("net.maddkraft.maddprestige.platform.paper.event.PostPrestigeEvent", listener,
                event -> postPrestige.put(snapshot(event).playerId(), snapshot(event)));
    }

    @SuppressWarnings("unchecked")
    private void registerEvent(String name, Listener listener, Consumer<Event> consumer) {
        try {
            Class<? extends Event> type = (Class<? extends Event>) Class.forName(name);
            plugin.getServer().getPluginManager().registerEvent(type, listener, EventPriority.NORMAL,
                    (ignored, event) -> consumer.accept(event), plugin, false);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Production lifecycle event is absent: " + name, exception);
        }
    }

    private void registerEconomy() {
        controlEconomy("registerService", new Class<?>[0]);
    }

    private void unregisterEconomy() {
        controlEconomy("unregisterService", new Class<?>[0]);
    }

    private void setEconomyMode(String mode) {
        controlEconomy("setMode", new Class<?>[] {String.class}, mode);
    }

    private int withdrawals() {
        return (int) controlEconomy("withdrawals", new Class<?>[0]);
    }

    private int deposits() {
        return (int) controlEconomy("deposits", new Class<?>[0]);
    }

    private UUID fundedPlayer() {
        UUID player = UUID.randomUUID();
        controlEconomy("setBalance", new Class<?>[] {UUID.class, double.class}, player, 100D);
        return player;
    }

    private double balance(UUID player) {
        return economy.getBalance(plugin.getServer().getOfflinePlayer(player));
    }

    private Object controlEconomy(String method, Class<?>[] parameterTypes, Object... arguments) {
        require(economyController != null, "controlled Economy plugin is absent");
        try {
            return economyController.getClass().getMethod(method, parameterTypes).invoke(economyController, arguments);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Controlled Economy operation failed: " + method, exception);
        }
    }

    private long playerRows(UUID player) {
        Plugin production = plugin.getServer().getPluginManager().getPlugin(PLUGIN_NAME);
        require(production != null, "production plugin is absent");
        try {
            Class<?> type = Class.forName("org.sqlite.JDBC", true, production.getClass().getClassLoader());
            Driver driver = (Driver) type.getConstructor().newInstance();
            java.nio.file.Path database = production.getDataFolder().toPath().resolve("maddprestige-v2.sqlite");
            try (Connection connection = driver.connect("jdbc:sqlite:" + database.toAbsolutePath(),
                    new java.util.Properties());
                    PreparedStatement statement = connection.prepareStatement(
                            "SELECT COUNT(*) FROM mp_player_stage_state WHERE player_uuid=?")) {
                statement.setString(1, player.toString());
                try (java.sql.ResultSet rows = statement.executeQuery()) {
                    require(rows.next(), "player row count returned no result");
                    return rows.getLong(1);
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot inspect disposable player state", exception);
        }
    }

    private void assertExactDependencies() {
        EXACT_DEPENDENCIES.forEach((name, version) -> {
            Plugin dependency = plugin.getServer().getPluginManager().getPlugin(name);
            require(dependency != null && dependency.isEnabled(), name + " is absent or disabled");
            require(version.equals(dependency.getPluginMeta().getVersion()), name + " version is "
                    + dependency.getPluginMeta().getVersion() + ", expected " + version);
        });
    }

    private void eventuallyEligible(UUID player, Runnable success) {
        AtomicReference<CompletableFuture<ServiceResult<
                net.maddkraft.maddprestige.api.service.OperationEvaluation>>> pending =
                new AtomicReference<>();
        coordinator.eventually("eligible evaluation recovery", Duration.ofSeconds(30), () -> {
            CompletableFuture<ServiceResult<net.maddkraft.maddprestige.api.service.OperationEvaluation>> current =
                    pending.updateAndGet(existing -> existing == null
                            ? service.evaluateRankUp(player).toCompletableFuture()
                            : existing);
            if (!current.isDone()) return false;
            ServiceResult<net.maddkraft.maddprestige.api.service.OperationEvaluation> result = current.join();
            if (result.successful() && result.value().orElseThrow().status()
                    == OperationEvaluationStatus.ELIGIBLE) return true;
            pending.compareAndSet(current, null);
            return false;
        }, success);
    }

    private boolean providerVisible(String id) {
        if (service == null) return false;
        ServiceResult<List<ProviderView>> result = service.providers();
        if (!result.successful()) return false;
        List<ProviderView> matches = result.value().orElseThrow().stream()
                .filter(provider -> provider.id().value().equals(id)).toList();
        return matches.size() == 1 && providerUsable(matches.getFirst().health());
    }

    private boolean providerUnavailableOrAbsent(String id) {
        if (service == null) return true;
        ServiceResult<List<ProviderView>> result = service.providers();
        if (!result.successful()) return false;
        List<ProviderView> matches = result.value().orElseThrow().stream()
                .filter(provider -> provider.id().value().equals(id)).toList();
        return matches.isEmpty() || matches.stream().noneMatch(
                provider -> providerUsable(provider.health()));
    }

    private boolean providerUsable(ProviderHealthState health) {
        return health == ProviderHealthState.AVAILABLE || health == ProviderHealthState.ACTIVE;
    }

    private long providerCount(String id) {
        if (service == null) return 0;
        ServiceResult<List<ProviderView>> result = service.providers();
        if (!result.successful()) return 0;
        return result.value().orElseThrow().stream().filter(provider -> provider.id().value().equals(id)).count();
    }

    private void providerCommand(String command, String... arguments) {
        org.bukkit.command.PluginCommand registered = plugin.getServer().getPluginCommand(command);
        require(registered != null && registered.execute(sender, command, arguments),
                "provider command failed: " + command + " " + Arrays.toString(arguments));
    }

    private int alphaCancellations() {
        return alphaObservation("observedCancellations");
    }

    private int alphaObservation(String method) {
        Plugin alpha = plugin.getServer().getPluginManager().getPlugin("Phase8E-Alpha");
        require(alpha != null, "Phase8E-Alpha plugin is absent");
        try {
            return ((Number) alpha.getClass().getMethod(method).invoke(alpha)).intValue();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot inspect controlled Alpha observation " + method, failure);
        }
    }

    private void addSetupStart(String passMessage) {
        steps.add(() -> command(SETUP_START, lines -> {
            setupSession = UUID.fromString(extract(UUID_PATTERN, lines));
            pass(passMessage);
            coordinator.advance();
        }));
    }

    private void addSetupActions(SetupAction... actions) {
        for (SetupAction action : actions) {
            steps.add(commandStep(action.command(), action.passMessage()));
        }
    }

    private void addSetupPublication(
            String previewMessage,
            String acknowledgedMessage,
            String confirmationNote,
            String confirmedPrefix) {
        steps.add(commandStep(() -> SETUP_PREVIEW + setupSession, previewMessage));
        steps.add(() -> command(SETUP_ACKNOWLEDGE + setupSession, lines -> {
            setupSession = UUID.fromString(extract(UUID_PATTERN, lines));
            pass(acknowledgedMessage);
            coordinator.advance();
        }));
        steps.add(() -> command(SETUP_CONFIRM + setupSession + " " + confirmationNote, lines -> {
            revision = extract(REVISION_PATTERN, lines);
            pass(confirmedPrefix + revision);
            coordinator.advance();
        }));
    }

    private static SetupAction setupAction(java.util.function.Supplier<String> command, String passMessage) {
        return new SetupAction(command, passMessage);
    }

    private void enableFaultIntegrations(Runnable success) {
        command("maddprestige config draft", lines -> {
            String draft = extract(UUID_PATTERN, lines);
            setIntegration(draft, List.of(
                    "integrations.vault.enabled",
                    "integrations.mcmmo.enabled",
                    "integrations.placeholderapi.output.enabled",
                    "integrations.economyshopgui.compatibility-enabled",
                    "integrations.quickshop.compatibility-enabled",
                    "integrations.griefprevention.enabled",
                    "integrations.worldguard.enabled",
                    "integrations.craftengine.enabled"), 0, () ->
                    command("maddprestige config validate " + draft, validated ->
                            command("maddprestige config apply " + draft + " " + revision
                                    + " phase8e exact fault integrations", applied -> {
                                revision = extract(REVISION_PATTERN, applied);
                                pass("all supported exact dependency integrations enabled by canonical revision "
                                        + revision);
                                success.run();
                            })));
        });
    }

    private void setIntegration(String draft, List<String> keys, int index, Runnable success) {
        if (index == keys.size()) {
            success.run();
            return;
        }
        command("maddprestige config set " + draft + " " + keys.get(index) + " true",
                ignored -> setIntegration(draft, keys, index + 1, success));
    }

    private Runnable commandStep(java.util.function.Supplier<String> command, String label) {
        return coordinator.commandStep(command, label, this::pass);
    }

    private void command(String command, Consumer<List<String>> continuation) {
        coordinator.command(command, continuation);
    }

    private void commandFails(String command, String label, Runnable continuation) {
        messages.clear();
        String[] tokens = command.split(" +");
        require(tokens.length > 0 && tokens[0].equals(COMMAND_NAME), "unexpected command ingress");
        org.bukkit.command.PluginCommand registered = plugin.getServer().getPluginCommand(COMMAND_NAME);
        require(registered != null && registered.execute(sender, COMMAND_NAME,
                Arrays.copyOfRange(tokens, 1, tokens.length)), "command rejected before diagnostic: " + command);
        coordinator.eventually("expected command failure " + command, () -> !messages.isEmpty(), () -> {
            List<String> snapshot = List.copyOf(messages);
            plugin.getLogger().info("PHASE8E-COMMAND-EXPECTED-FAILURE " + command + " -> " + snapshot);
            require(snapshot.stream().anyMatch(coordinator::failureDiagnostic),
                    "unconfigurable selection did not fail: " + command + " -> " + snapshot);
            pass(label + " was rejected before preview/publication with an actionable diagnostic");
            continuation.run();
        });
    }

    private static String extract(Pattern pattern, List<String> lines) {
        return Phase8EQualificationSupport.extract(pattern, lines);
    }

    private static OperationEventSnapshot snapshot(Event event) {
        try {
            Method method = event.getClass().getMethod("snapshot");
            return (OperationEventSnapshot) method.invoke(event);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot read production event snapshot", exception);
        }
    }

    private static void assertEventPair(UUID player, OperationResult result,
            Map<UUID, OperationEventSnapshot> pre, Map<UUID, OperationEventSnapshot> post,
            OperationStatus expected) {
        OperationEventSnapshot before = requireSnapshot(pre, player, "PRE");
        OperationEventSnapshot after = requireSnapshot(post, player, "POST");
        require(before.correlationId().equals(result.requestId())
                && after.correlationId().equals(result.requestId()), "event request identity changed");
        require(before.durableOperationId().isEmpty(), "PRE exposed a durable operation identity");
        require(after.durableOperationId().equals(result.durableOperationId()),
                "POST/result durable identities differ");
        require(after.terminalStatus().orElseThrow() == expected, "POST terminal status differs: " + after);
    }

    private void assertPostStatus(UUID player, OperationResult result, OperationStatus expected) {
        assertEventPair(player, result, preRank, postRank, expected);
    }

    private static OperationEventSnapshot requireSnapshot(
            Map<UUID, OperationEventSnapshot> values, UUID player, String label) {
        OperationEventSnapshot value = values.get(player);
        require(value != null, label + " snapshot is absent for " + player);
        return value;
    }

    private static OperationResult completed(ServiceResult<OperationResult> result, String label) {
        OperationResult value = value(result, label);
        require(value.status() == OperationStatus.COMPLETED && value.durableOperationId().isPresent(),
                label + " did not complete durably: " + value);
        return value;
    }

    private static <T> T value(ServiceResult<T> result, String label) {
        require(result.successful(), label + " service failure: " + result.error());
        return result.value().orElseThrow();
    }

    private void pass(String message) {
        passed++;
        plugin.getLogger().info("PHASE8E-Q PASS " + passed + " " + message);
    }

    private void fail(String label, Throwable failure) {
        if (stopping) return;
        stopping = true;
        plugin.getLogger().log(java.util.logging.Level.SEVERE, "PHASE8E-Q FAIL " + label, failure);
        finish("PHASE8E-Q FAILED mode=" + mode + " pass-count=" + passed);
    }

    private void finish(String message) {
        stopping = true;
        plugin.getLogger().info(message);
        plugin.getServer().getScheduler().runTaskLater(plugin, plugin.getServer()::shutdown, 20L);
    }

    private static void require(boolean condition, String message) {
        Phase8EQualificationSupport.require(condition, message);
    }

}
