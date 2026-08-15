package gg.maddkraft.prestige.integration;

import gg.maddkraft.prestige.api.ProgressType;
import gg.maddkraft.prestige.config.PluginSettings;
import gg.maddkraft.prestige.service.LedgerService;
import org.bukkit.OfflinePlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.function.Consumer;

public final class InstalledPluginHooks {
    private final JavaPlugin plugin;
    private final LedgerService ledger;
    private final CompatibilityService compatibility;
    private final IntegrationRelationshipService relationships;
    private final Listener dynamicListener = new Listener() {};

    public InstalledPluginHooks(JavaPlugin plugin, LedgerService ledger, CompatibilityService compatibility,
                                IntegrationRelationshipService relationships) {
        this.plugin = plugin;
        this.ledger = ledger;
        this.compatibility = compatibility;
        this.relationships = relationships;
    }

    public void registerAll() {
        if (plugin.getConfig().getBoolean("integrations.mcmmo.enabled", true)) registerMcMmo();
        registerEconomyShopGui();
        registerQuickShop();
        registerUltimateMobCoins();
        registerConfiguredEventHooks();
    }

    private void registerMcMmo() {
        if (!compatibility.configured("mcMMO")) return;
        registerDynamic(
                "mcMMO",
                "com.gmail.nossr50.events.experience.McMMOPlayerXpGainEvent",
                event -> {
                    Player player = invoke(event, "getPlayer", Player.class).orElse(null);
                    Number amount = invoke(event, "getXpGained", Number.class).orElse(null);
                    if (player != null && amount != null) creditMcMmo(player.getUniqueId(), Math.max(0L, amount.longValue()));
                }
        );
    }

    private void registerEconomyShopGui() {
        if (!compatibility.configured("EconomyShopGUI")) return;
        double weight = clampWeight(plugin.getConfig().getDouble("integrations.installed-stack.economyshopgui-earned-weight", 1.0));
        if (weight <= 0.0) return;
        registerDynamic(
                "EconomyShopGUI",
                "me.gypopo.economyshopgui.api.events.PostTransactionEvent",
                event -> {
                    Object result = invoke(event, "getTransactionResult", Object.class).orElse(null);
                    Object type = invoke(event, "getTransactionType", Object.class).orElse(null);
                    Player player = invoke(event, "getPlayer", Player.class).orElse(null);
                    Number price = invoke(event, "getPrice", Number.class).orElse(null);
                    if (result == null || type == null || player == null || price == null) return;
                    if (!result.toString().startsWith("SUCCESS") || !type.toString().contains("SELL")) return;
                    creditServerEarnings(player.getUniqueId(), Math.abs(price.doubleValue()) * weight, "EconomyShopGUI");
                }
        );
    }

    private void registerQuickShop() {
        if (!compatibility.configured("QuickShop-Hikari")) return;
        double weight = clampWeight(plugin.getConfig().getDouble("integrations.installed-stack.quickshop-earned-weight", 0.25));
        if (weight <= 0.0) return;
        registerDynamic(
                "QuickShop-Hikari",
                "com.ghostchu.quickshop.api.event.economy.ShopSuccessPurchaseEvent",
                event -> {
                    Object shop = invoke(event, "getShop", Object.class).orElse(null);
                    Number balance = invoke(event, "getBalanceWithoutTax", Number.class).orElse(null);
                    if (shop == null || balance == null) return;
                    boolean buying = invoke(shop, "isBuying", Boolean.class).orElse(false);
                    Object recipient = buying
                            ? invoke(event, "getPurchaser", Object.class).orElse(null)
                            : invoke(shop, "getOwner", Object.class).orElse(null);
                    if (recipient == null) return;
                    Optional<UUID> playerId = invoke(recipient, "getUniqueId", UUID.class);
                    if (playerId.isEmpty()) {
                        Object optional = invoke(recipient, "getUniqueIdIfRealPlayer", Object.class).orElse(null);
                        if (optional instanceof Optional<?> raw && raw.orElse(null) instanceof UUID uuid) playerId = Optional.of(uuid);
                    }
                    playerId.ifPresent(uuid -> creditServerEarnings(uuid, Math.abs(balance.doubleValue()) * weight, "QuickShop"));
                }
        );
    }

    private void registerUltimateMobCoins() {
        if (!compatibility.configured("UltimateMobCoins")) return;
        double weight = clampWeight(plugin.getConfig().getDouble("integrations.installed-stack.ultimate-mobcoins-earned-weight", 0.0));
        if (weight <= 0.0) return;
        registerDynamic(
                "UltimateMobCoins",
                "nl.chimpgamer.ultimatemobcoins.paper.events.MobCoinsReceiveEvent",
                event -> {
                    Player player = invoke(event, "getPlayer", Player.class).orElse(null);
                    Object rawAmount = invoke(event, "getAmount", Object.class).orElse(null);
                    double amount = rawAmount instanceof BigDecimal decimal ? decimal.doubleValue()
                            : rawAmount instanceof Number number ? number.doubleValue() : 0.0;
                    if (player != null && amount > 0.0) creditServerEarnings(player.getUniqueId(), amount * weight, "UltimateMobCoins");
                }
        );
    }

    private void registerConfiguredEventHooks() {
        PluginSettings settings = PluginSettings.load(plugin.getConfig());
        if (!settings.relationships().enabled()) return;
        for (Map.Entry<String, PluginSettings.ExternalRelationship> provider
                : settings.relationships().providers().entrySet()) {
            PluginSettings.ExternalRelationship relationship = provider.getValue();
            if (!relationship.enabled() || relationship.eventHooks().isEmpty()) continue;
            Plugin dependency = relationship.pluginNames().stream()
                    .map(name -> plugin.getServer().getPluginManager().getPlugin(name))
                    .filter(candidate -> candidate != null && candidate.isEnabled())
                    .findFirst().orElse(null);
            if (dependency == null && relationship.requirePlugin()) continue;
            if (dependency == null) {
                plugin.getLogger().warning("Relationship provider " + provider.getKey()
                        + " has event hooks but require-plugin is false and no class loader is available; hooks skipped.");
                continue;
            }
            for (PluginSettings.ConfiguredEventHook hook : relationship.eventHooks()) {
                try {
                    ProgressType.valueOf(hook.progressType());
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().warning("Unknown progress type " + hook.progressType()
                            + " in relationship " + provider.getKey() + "; hook " + hook.id() + " skipped.");
                    continue;
                }
                registerDynamic(dependency, relationship.displayName(), hook.eventClass(), event ->
                        handleConfiguredEvent(provider.getKey(), hook, event), hook.ignoreCancelled());
            }
        }
    }

    private void handleConfiguredEvent(String provider, PluginSettings.ConfiguredEventHook hook, Object event) {
        if (!relationships.acceptsInbound(provider)) return;
        for (Map.Entry<String, String> condition : hook.equalsConditions().entrySet()) {
            Object actual = resolvePath(event, condition.getKey()).orElse(null);
            if (actual == null || !actual.toString().equalsIgnoreCase(condition.getValue())) return;
        }
        Object rawPlayer = resolvePath(event, hook.playerPath()).orElse(null);
        UUID playerId = playerId(rawPlayer).orElse(null);
        if (playerId == null) return;
        double amount = hook.fixedAmount();
        if (!hook.amountPath().isBlank()) {
            Object rawAmount = resolvePath(event, hook.amountPath()).orElse(null);
            if (rawAmount instanceof BigDecimal decimal) amount = decimal.doubleValue();
            else if (rawAmount instanceof Number number) amount = number.doubleValue();
            else return;
        }
        amount *= hook.weight();
        if (!Double.isFinite(amount) || amount <= 0.0) return;
        ProgressType type;
        try {
            type = ProgressType.valueOf(hook.progressType());
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Unknown progress type " + hook.progressType() + " in relationship " + provider);
            return;
        }
        double creditedAmount = amount;
        runOnServerThread(() -> {
            switch (type) {
                case SERVER_EARNINGS -> ledger.addServerEarnings(playerId, creditedAmount, hook.source());
                case MCMMO_XP -> ledger.addMcMmoXp(playerId, Math.round(creditedAmount));
                case RABBIT_HOLE -> ledger.addRabbitHole(playerId, (int) Math.round(creditedAmount));
                case DECREE_OBJECTIVE -> ledger.addDecreeObjective(playerId, (int) Math.round(creditedAmount));
                case BOSS -> ledger.addBoss(playerId, (int) Math.round(creditedAmount));
            }
        });
    }

    private void creditServerEarnings(UUID playerId, double amount, String source) {
        runOnServerThread(() -> ledger.addServerEarnings(playerId, amount, source));
    }

    private void creditMcMmo(UUID playerId, long amount) {
        runOnServerThread(() -> ledger.addMcMmoXp(playerId, amount));
    }

    private void runOnServerThread(Runnable action) {
        if (Bukkit.isPrimaryThread()) action.run();
        else plugin.getServer().getScheduler().runTask(plugin, action);
    }

    @SuppressWarnings("unchecked")
    private void registerDynamic(String pluginName, String className, Consumer<Object> handler) {
        Plugin dependency = compatibility.findEnabledPlugin(pluginName);
        if (dependency == null) return;
        registerDynamic(dependency, pluginName, className, handler, true);
    }

    @SuppressWarnings("unchecked")
    private void registerDynamic(Plugin dependency, String pluginName, String className,
                                 Consumer<Object> handler, boolean ignoreCancelled) {
        try {
            Class<?> raw = Class.forName(className, false, dependency.getClass().getClassLoader());
            if (!Event.class.isAssignableFrom(raw)) throw new IllegalArgumentException(className + " is not a Bukkit event");
            Class<? extends Event> eventClass = (Class<? extends Event>) raw;
            EventExecutor executor = (listener, event) -> {
                try {
                    handler.accept(event);
                } catch (RuntimeException exception) {
                    plugin.getLogger().warning("Compatibility hook for " + pluginName + " rejected an event: " + exception.getMessage());
                }
            };
            PluginManager manager = plugin.getServer().getPluginManager();
            manager.registerEvent(eventClass, dynamicListener, EventPriority.MONITOR, executor, plugin, ignoreCancelled);
            plugin.getLogger().info("Hooked " + pluginName + " through " + eventClass.getSimpleName());
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("Could not hook " + pluginName + ": " + exception.getMessage());
        }
    }

    private Optional<Object> resolvePath(Object target, String path) {
        Object current = target;
        for (String segment : path.split("\\.")) {
            if (current instanceof Optional<?> optional) current = optional.orElse(null);
            if (current == null || !segment.matches("[A-Za-z_$][A-Za-z0-9_$]*")) return Optional.empty();
            try {
                Method method = current.getClass().getMethod(segment);
                current = method.invoke(current);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                return Optional.empty();
            }
        }
        if (current instanceof Optional<?> optional) current = optional.orElse(null);
        return Optional.ofNullable(current);
    }

    private Optional<UUID> playerId(Object value) {
        if (value instanceof Player player) return Optional.of(player.getUniqueId());
        if (value instanceof OfflinePlayer player) return Optional.of(player.getUniqueId());
        if (value instanceof UUID uuid) return Optional.of(uuid);
        if (value instanceof String text) {
            try { return Optional.of(UUID.fromString(text)); } catch (IllegalArgumentException ignored) { }
        }
        return invoke(value, "getUniqueId", UUID.class);
    }

    private static <T> Optional<T> invoke(Object target, String methodName, Class<T> expected) {
        if (target == null) return Optional.empty();
        try {
            Method method = target.getClass().getMethod(methodName);
            Object result = method.invoke(target);
            return expected.isInstance(result) ? Optional.of(expected.cast(result)) : Optional.empty();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private double clampWeight(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
