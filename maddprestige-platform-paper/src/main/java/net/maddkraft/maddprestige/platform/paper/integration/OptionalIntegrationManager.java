package net.maddkraft.maddprestige.platform.paper.integration;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.core.manual.ManualMetricHandle;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.integrations.IntegrationProviderLifecycle;
import net.maddkraft.maddprestige.integrations.IntegrationProviderLifecycle.ManagedProvider;
import net.maddkraft.maddprestige.integrations.IntegrationTaskScheduler;
import net.maddkraft.maddprestige.integrations.config.IntegrationConfiguration;
import net.maddkraft.maddprestige.integrations.config.IntegrationPlan;
import net.maddkraft.maddprestige.integrations.placeholder.PlaceholderInputMetricProvider;
import net.maddkraft.maddprestige.platform.paper.placeholder.MaddPrestigePlaceholderCache;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Optional dependency discovery, exact generation ownership, and reverse-safe shutdown. */
public final class OptionalIntegrationManager implements Listener {
    private static final List<String> RECONCILIATION_ORDER = List.of(
            "Vault", "mcMMO", "PlaceholderAPI", "EconomyShopGUI", "QuickShop-Hikari",
            "GriefPrevention", "WorldGuard", "CraftEngine");
    private static final Map<String, List<String>> SUPPORTED = Map.of(
            "Vault", List.of("2.20.2"),
            "mcMMO", List.of("2.2.053"),
            "PlaceholderAPI", List.of("2.12.2", "2.12.3"),
            "EconomyShopGUI", List.of("7.2.0"),
            "QuickShop-Hikari", List.of("6.2.0.11"),
            "GriefPrevention", List.of("16.18.7"),
            "WorldGuard", List.of("7.0.18+2392-fa605e6"),
            "CraftEngine", List.of("26.7.4"));
    private static final String VAULT_ECONOMY_SERVICE = "net.milkbowl.vault.economy.Economy";

    private final Plugin owner;
    private final ProviderRegistry registry;
    private final IntegrationTaskScheduler scheduler;
    private final ManualMetricHandle mcMmoAdjustedXp;
    private final MaddPrestigePlaceholderCache placeholderOutput;
    private final Clock clock;
    private final Map<String, RuntimeBinding> bindings = new LinkedHashMap<>();
    private final BindingAttemptGuard bindingAttempts = new BindingAttemptGuard();
    private final CraftEngineRegistryLifecycle craftEngine = new CraftEngineRegistryLifecycle();
    private IntegrationConfiguration configuration = IntegrationConfiguration.disabled();
    private Set<ProviderId> desired = Set.of();
    private boolean setupDiscovery;
    private Listener craftEngineReloadListener;
    private BukkitTask craftEngineProbeTask;

    public OptionalIntegrationManager(Plugin owner, ProviderRegistry registry,
            IntegrationTaskScheduler scheduler, ManualMetricHandle mcMmoAdjustedXp,
            MaddPrestigePlaceholderCache placeholderOutput, Clock clock) {
        this.owner = java.util.Objects.requireNonNull(owner, "owner");
        this.registry = java.util.Objects.requireNonNull(registry, "registry");
        this.scheduler = java.util.Objects.requireNonNull(scheduler, "scheduler");
        this.mcMmoAdjustedXp = java.util.Objects.requireNonNull(mcMmoAdjustedXp, "mcMMO adjusted XP handle");
        this.placeholderOutput = java.util.Objects.requireNonNull(placeholderOutput, "placeholder output cache");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    public void start(IntegrationConfiguration initial) {
        start(initial, false);
    }

    /** Starts exact optional discovery; a dormant first boot may expose active providers only to setup validation. */
    public void start(IntegrationConfiguration initial, boolean firstBootSetupDiscovery) {
        configuration = java.util.Objects.requireNonNull(initial, "integration configuration");
        desired = composition(configuration).reachableProviders();
        setupDiscovery = firstBootSetupDiscovery;
        owner.getServer().getPluginManager().registerEvents(this, owner);
        RECONCILIATION_ORDER.stream().filter(name -> !"CraftEngine".equals(name)).forEach(this::bindIfAvailable);
        observeCraftEngineAtStartup();
    }

    /** Bounded cache shared by the production state publisher and PlaceholderAPI hot path. */
    public MaddPrestigePlaceholderCache placeholderOutput() {
        return placeholderOutput;
    }

    public void reconcile(IntegrationConfiguration replacement) {
        IntegrationConfiguration prior = configuration;
        configuration = java.util.Objects.requireNonNull(replacement, "integration configuration");
        desired = composition(configuration).reachableProviders();
        setupDiscovery = false;
        for (String name : RECONCILIATION_ORDER) {
            duringBindingAttempt(name, () -> reconcileWithinAttempt(name, prior));
        }
    }

    private void reconcileWithinAttempt(String name, IntegrationConfiguration prior) {
        if (!relevant(name) && !setupDiscovery) {
            if ("CraftEngine".equals(name)) {
                unbindCraftEngine("Canonical integration configuration disabled CraftEngine");
            } else {
                unbind(name, "Canonical integration configuration disabled the integration");
            }
            return;
        }
        if ("CraftEngine".equals(name)) {
            if (craftEngine.state() == CraftEngineRegistryLifecycle.State.AVAILABLE
                    && (prior.craftEngineRewardMaximumQuantity()
                            != configuration.craftEngineRewardMaximumQuantity()
                            || !bindings.containsKey(name))) {
                rebindReadyCraftEngineWithinAttempt();
            } else {
                reconcileBinding(name);
            }
        } else if (structuralChange(name, prior, configuration)) {
            unbind(name, "Canonical integration configuration changed");
            bindIfAvailableWithinAttempt(name);
        } else {
            reconcileBinding(name);
            if (!bindings.containsKey(name)) {
                bindIfAvailableWithinAttempt(name);
            }
        }
    }

    public List<String> statusLines() {
        return List.of("[integration.craftengine] state=" + craftEngine.state(),
                "[integration.optional] bound=" + String.join(",", bindings.keySet()));
    }

    static Map<String, List<String>> qualifiedDependencies() {
        return SUPPORTED;
    }

    static IntegrationPlan composition(IntegrationConfiguration configured) {
        return IntegrationPlan.from(configured);
    }

    public void stop() {
        HandlerList.unregisterAll(this);
        cancelCraftEngineProbe();
        if (craftEngineReloadListener != null) {
            HandlerList.unregisterAll(craftEngineReloadListener);
            craftEngineReloadListener = null;
        }
        List.copyOf(bindings.entrySet()).reversed().forEach(entry ->
                entry.getValue().stop("MaddPrestige is shutting down"));
        bindings.clear();
        craftEngine.dependencyAbsent();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        String name = event.getPlugin().getName();
        if ("CraftEngine".equals(name)) {
            observeCraftEngineReEnable();
        } else {
            bindIfAvailable(name);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        String name = event.getPlugin().getName();
        if ("CraftEngine".equals(name)) {
            disableCraftEngine("CraftEngine dependency was disabled");
        } else {
            duringBindingAttempt(name, () -> unbind(name, "Dependency was disabled"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServiceRegister(ServiceRegisterEvent event) {
        if (VAULT_ECONOMY_SERVICE.equals(event.getProvider().getService().getName())) {
            duringBindingAttempt("Vault", () -> {
                unbind("Vault", "Vault Economy service generation changed");
                bindIfAvailableWithinAttempt("Vault");
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServiceUnregister(ServiceUnregisterEvent event) {
        if (VAULT_ECONOMY_SERVICE.equals(event.getProvider().getService().getName())) {
            duringBindingAttempt("Vault", () -> {
                unbind("Vault", "Vault Economy service was unregistered");
                bindIfAvailableWithinAttempt("Vault");
            });
        }
    }

    private void bindIfAvailable(String name) {
        duringBindingAttempt(name, () -> bindIfAvailableWithinAttempt(name));
    }

    private void bindIfAvailableWithinAttempt(String name) {
        if (!SUPPORTED.containsKey(name) || "CraftEngine".equals(name) || bindings.containsKey(name)
                || (!relevant(name) && !setupDiscovery)) {
            return;
        }
        Plugin dependency = owner.getServer().getPluginManager().getPlugin(name);
        if (dependency == null || !dependency.isEnabled()) {
            return;
        }
        String detected = dependency.getPluginMeta().getVersion();
        if (!supportsVersion(name, detected)) {
            owner.getLogger().warning("Optional " + name + " " + detected + " is unsupported; expected "
                    + expectedVersions(name) + ". Its capabilities remain unavailable.");
            return;
        }
        try {
            bindings.put(name, createBinding(name, dependency, detected));
        } catch (IllegalStateException exception) {
            owner.getLogger().info("Optional " + name + " is present but not ready: " + exception.getMessage());
        } catch (LinkageError | RuntimeException exception) {
            owner.getLogger().log(Level.SEVERE,
                    "Optional " + name + " binding failed closed; its capabilities remain unavailable", exception);
        }
    }

    private void duringBindingAttempt(String name, Runnable action) {
        if (!bindingAttempts.begin(name)) {
            return;
        }
        try {
            action.run();
        } finally {
            bindingAttempts.end(name);
        }
    }

    private RuntimeBinding createBinding(String name, Plugin dependency, String version) {
        ArrayList<ManagedProvider> providers = new ArrayList<>();
        ArrayList<Listener> listeners = new ArrayList<>();
        PlaceholderInputMetricProvider placeholderInput = null;
        Runnable activate = () -> { };
        Runnable cleanup = () -> { };
        switch (name) {
            case "Vault" -> providers.addAll(VaultIntegrationBootstrap.create(
                    owner.getServer(), registry, scheduler, clock, version));
            case "mcMMO" -> {
                McMmoIntegrationBootstrap.Binding binding = McMmoIntegrationBootstrap.create(
                        registry, scheduler, mcMmoAdjustedXp, clock, version);
                providers.addAll(binding.providers());
                listeners.add(binding.listener());
            }
            case "PlaceholderAPI" -> {
                PlaceholderApiIntegrationBootstrap.Binding binding = PlaceholderApiIntegrationBootstrap.create(
                        owner.getServer(), registry, scheduler, configuration.placeholderInputs(),
                        configuration.placeholderOutputEnabled(), placeholderOutput,
                        owner.getPluginMeta().getVersion(), clock, version);
                providers.addAll(configuration.placeholderInputs().isEmpty() ? List.of() : binding.providers());
                placeholderInput = configuration.placeholderInputs().isEmpty() ? null : binding.provider();
                activate = binding.activate();
                cleanup = binding.cleanup();
            }
            case "EconomyShopGUI" -> listeners.add(EconomyShopGuiIntegrationBootstrap.create(() ->
                    configuration.economyShopGuiCompatibilityEnabled()));
            case "QuickShop-Hikari" -> listeners.add(QuickShopIntegrationBootstrap.create(() ->
                    configuration.quickShopCompatibilityEnabled()));
            case "GriefPrevention" -> providers.addAll(GriefPreventionIntegrationBootstrap.create(
                    dependency, registry, scheduler, clock, version));
            case "WorldGuard" -> providers.addAll(WorldGuardIntegrationBootstrap.create(
                    owner.getServer(), registry, scheduler, clock, version));
            case "CraftEngine" -> providers.addAll(CraftEngineIntegrationBootstrap.create(
                    owner.getServer(), registry, scheduler, clock,
                    configuration.craftEngineRewardMaximumQuantity(), version));
            default -> throw new IllegalArgumentException("Unsupported optional integration: " + name);
        }

        IntegrationProviderLifecycle lifecycle = providers.isEmpty()
                ? null : new IntegrationProviderLifecycle(registry, "maddprestige");
        ArrayList<BukkitTask> tasks = new ArrayList<>();
        Runnable activation = activate;
        Runnable resourceCleanup = cleanup;
        try {
            if (lifecycle != null) {
                lifecycle.discover(providers);
                lifecycle.reconcileActiveProviders(setupDiscovery
                        ? lifecycle.registrations().keySet() : desired);
            }
            listeners.forEach(listener -> owner.getServer().getPluginManager().registerEvents(listener, owner));
            activation.run();
            if (placeholderInput != null) {
                PlaceholderInputMetricProvider input = placeholderInput;
                tasks.add(owner.getServer().getScheduler().runTaskTimer(owner,
                        () -> owner.getServer().getOnlinePlayers().forEach(player ->
                                input.refresh(player.getUniqueId()).exceptionally(failure -> {
                                    owner.getLogger().log(Level.WARNING,
                                            "Placeholder input refresh failed closed", failure);
                                    return 0;
                                })), 1L, 20L));
            }
            return new RuntimeBinding(lifecycle, listeners, tasks, resourceCleanup);
        } catch (RuntimeException exception) {
            tasks.forEach(BukkitTask::cancel);
            listeners.forEach(HandlerList::unregisterAll);
            resourceCleanup.run();
            if (lifecycle != null) {
                lifecycle.dependencyUnavailable("Optional integration startup rolled back");
            }
            throw exception;
        }
    }

    private void observeCraftEngineAtStartup() {
        Plugin dependency = compatibleCraftEngine();
        if (dependency == null) {
            craftEngine.dependencyAbsent();
            return;
        }
        craftEngine.dependencyPresent();
        installCraftEngineReloadListener();
        craftEngineProbeTask = owner.getServer().getScheduler().runTaskLater(owner, () -> {
            craftEngineProbeTask = null;
            try {
                handleCraftEngineTransition(craftEngine.startupProbe(CraftEngineReadinessProbe.ready()));
            } catch (LinkageError | RuntimeException exception) {
                craftEngine.bindingFailed();
                owner.getLogger().log(Level.SEVERE, "CraftEngine startup readiness probe failed closed", exception);
            }
        }, 20L);
    }

    private void observeCraftEngineReEnable() {
        if (compatibleCraftEngine() == null) {
            craftEngine.bindingFailed();
            return;
        }
        unbindCraftEngine("CraftEngine re-enable requires a new completed registry reload");
        craftEngine.dependencyPresent();
        installCraftEngineReloadListener();
    }

    private Plugin compatibleCraftEngine() {
        Plugin dependency = owner.getServer().getPluginManager().getPlugin("CraftEngine");
        if (dependency == null || !dependency.isEnabled()) {
            return null;
        }
        String detected = dependency.getPluginMeta().getVersion();
        if (!supportsVersion("CraftEngine", detected)) {
            owner.getLogger().warning("Optional CraftEngine " + detected + " is unsupported; expected "
                    + expectedVersions("CraftEngine") + ". Its capabilities remain unavailable.");
            return null;
        }
        return dependency;
    }

    private void installCraftEngineReloadListener() {
        if (craftEngineReloadListener == null) {
            craftEngineReloadListener = new CraftEngineReloadListener(this::craftEngineReloadCompleted);
            owner.getServer().getPluginManager().registerEvents(craftEngineReloadListener, owner);
        }
    }

    private void craftEngineReloadCompleted() {
        cancelCraftEngineProbe();
        if (compatibleCraftEngine() == null) {
            disableCraftEngine("CraftEngine reload completed without a usable dependency");
            return;
        }
        handleCraftEngineTransition(craftEngine.reloadCompleted());
    }

    private void handleCraftEngineTransition(CraftEngineRegistryLifecycle.Transition transition) {
        try {
            duringBindingAttempt("CraftEngine", () -> handleCraftEngineTransitionWithinAttempt(transition));
        } catch (LinkageError | RuntimeException exception) {
            unbindCraftEngine("CraftEngine binding failed");
            craftEngine.bindingFailed();
            owner.getLogger().log(Level.SEVERE, "CraftEngine registry binding failed closed", exception);
        }
    }

    private void handleCraftEngineTransitionWithinAttempt(CraftEngineRegistryLifecycle.Transition transition) {
        switch (transition) {
            case BIND -> bindReadyCraftEngineWithinAttempt();
            case REBIND -> rebindReadyCraftEngineWithinAttempt();
            case UNBIND -> unbindCraftEngine("CraftEngine registry became unavailable");
            case NONE -> {
                // Waiting and absent states intentionally expose no provider registration.
            }
        }
    }

    private void rebindReadyCraftEngineWithinAttempt() {
        unbindCraftEngine("CraftEngine registry generation was replaced");
        bindReadyCraftEngineWithinAttempt();
    }

    private void bindReadyCraftEngineWithinAttempt() {
        Plugin dependency = compatibleCraftEngine();
        if (dependency == null) {
            craftEngine.bindingFailed();
            return;
        }
        bindings.put("CraftEngine", createBinding(
                "CraftEngine", dependency, dependency.getPluginMeta().getVersion()));
    }

    static boolean supportsVersion(String name, String detected) {
        List<String> versions = SUPPORTED.get(name);
        return versions != null && versions.contains(detected);
    }

    private static String expectedVersions(String name) {
        return String.join(" or ", SUPPORTED.get(name));
    }

    private void disableCraftEngine(String reason) {
        cancelCraftEngineProbe();
        unbindCraftEngine(reason);
        if (craftEngineReloadListener != null) {
            HandlerList.unregisterAll(craftEngineReloadListener);
            craftEngineReloadListener = null;
        }
        craftEngine.dependencyDisabled();
    }

    private void unbindCraftEngine(String reason) {
        RuntimeBinding binding = bindings.remove("CraftEngine");
        if (binding != null) {
            binding.stop(reason);
        }
    }

    private void cancelCraftEngineProbe() {
        if (craftEngineProbeTask != null) {
            craftEngineProbeTask.cancel();
            craftEngineProbeTask = null;
        }
    }

    private void reconcileBinding(String name) {
        RuntimeBinding binding = bindings.get(name);
        if (binding != null && binding.lifecycle() != null) {
            binding.lifecycle().reconcileActiveProviders(desired);
        }
    }

    private void unbind(String name, String reason) {
        RuntimeBinding binding = bindings.remove(name);
        if (binding != null) {
            binding.stop(reason);
        }
    }

    private boolean relevant(String name) {
        return switch (name) {
            case "Vault" -> configuration.vaultEnabled();
            case "mcMMO" -> configuration.mcMmoEnabled();
            case "PlaceholderAPI" -> configuration.placeholderOutputEnabled()
                    || !configuration.placeholderInputs().isEmpty();
            case "EconomyShopGUI" -> configuration.economyShopGuiCompatibilityEnabled();
            case "QuickShop-Hikari" -> configuration.quickShopCompatibilityEnabled();
            case "GriefPrevention" -> configuration.griefPreventionEnabled();
            case "WorldGuard" -> configuration.worldGuardEnabled();
            case "CraftEngine" -> configuration.craftEngineEnabled();
            default -> false;
        };
    }

    static boolean structuralChange(String name, IntegrationConfiguration prior,
            IntegrationConfiguration replacement) {
        return switch (name) {
            case "Vault" -> prior.vaultEnabled() != replacement.vaultEnabled();
            case "mcMMO" -> prior.mcMmoEnabled() != replacement.mcMmoEnabled();
            case "PlaceholderAPI" -> prior.placeholderOutputEnabled() != replacement.placeholderOutputEnabled()
                    || !prior.placeholderInputs().equals(replacement.placeholderInputs());
            case "EconomyShopGUI" -> prior.economyShopGuiCompatibilityEnabled()
                    != replacement.economyShopGuiCompatibilityEnabled();
            case "QuickShop-Hikari" -> prior.quickShopCompatibilityEnabled()
                    != replacement.quickShopCompatibilityEnabled();
            case "GriefPrevention" -> prior.griefPreventionEnabled() != replacement.griefPreventionEnabled();
            case "WorldGuard" -> prior.worldGuardEnabled() != replacement.worldGuardEnabled();
            case "CraftEngine" -> prior.craftEngineEnabled() != replacement.craftEngineEnabled()
                    || prior.craftEngineRewardMaximumQuantity()
                            != replacement.craftEngineRewardMaximumQuantity();
            default -> true;
        };
    }

    private record RuntimeBinding(
            IntegrationProviderLifecycle lifecycle,
            List<Listener> listeners,
            List<BukkitTask> tasks,
            Runnable cleanup) {
        private RuntimeBinding {
            listeners = List.copyOf(listeners);
            tasks = List.copyOf(tasks);
            java.util.Objects.requireNonNull(cleanup, "cleanup");
        }

        private void stop(String reason) {
            tasks.forEach(BukkitTask::cancel);
            listeners.forEach(HandlerList::unregisterAll);
            cleanup.run();
            if (lifecycle != null) {
                lifecycle.dependencyUnavailable(reason);
            }
        }
    }
}
