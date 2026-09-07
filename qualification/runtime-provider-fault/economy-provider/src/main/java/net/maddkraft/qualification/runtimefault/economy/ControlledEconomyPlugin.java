package net.maddkraft.qualification.runtimefault.economy;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/** Controlled Economy service loaded before MaddPrestige in disposable runtime provider/fault fault servers. */
public final class ControlledEconomyPlugin extends JavaPlugin {
    private final Map<UUID, Double> balances = new ConcurrentHashMap<>();
    private final AtomicInteger withdrawals = new AtomicInteger();
    private final AtomicInteger deposits = new AtomicInteger();
    private final AtomicReference<Mode> mode = new AtomicReference<>(Mode.SUCCESS);
    private Economy economy;
    private boolean registered;

    @Override
    public void onEnable() {
        economy = (Economy) Proxy.newProxyInstance(Economy.class.getClassLoader(), new Class<?>[] {Economy.class},
                this::economyCall);
        registerService();
        getLogger().info("RUNTIME-QUALIFICATION-ECONOMY enabled before MaddPrestige");
    }

    @Override
    public void onDisable() {
        unregisterService();
        getLogger().info("RUNTIME-QUALIFICATION-ECONOMY disabled withdrawals=" + withdrawals.get()
                + " deposits=" + deposits.get());
    }

    public void setMode(String replacement) {
        mode.set(Mode.valueOf(replacement.toUpperCase(Locale.ROOT)));
        getLogger().info("RUNTIME-QUALIFICATION-ECONOMY mode=" + mode.get());
    }

    public void setBalance(UUID playerId, double amount) {
        balances.put(java.util.Objects.requireNonNull(playerId, "player ID"), amount);
    }

    public double balance(UUID playerId) {
        return balances.getOrDefault(java.util.Objects.requireNonNull(playerId, "player ID"), 100D);
    }

    public int withdrawals() {
        return withdrawals.get();
    }

    public int deposits() {
        return deposits.get();
    }

    public void registerService() {
        if (registered) return;
        getServer().getServicesManager().register(Economy.class, economy, this, ServicePriority.Highest);
        registered = true;
        getLogger().info("RUNTIME-QUALIFICATION-ECONOMY service registered");
    }

    public void unregisterService() {
        if (!registered) return;
        getServer().getServicesManager().unregister(Economy.class, economy);
        registered = false;
        getLogger().info("RUNTIME-QUALIFICATION-ECONOMY service unregistered");
    }

    private Object economyCall(Object proxy, Method method, Object[] arguments) {
        String name = method.getName();
        return switch (name) {
            case "getName" -> "QualificationControlledEconomy";
            case "isEnabled" -> true;
            case "hasBankSupport" -> false;
            case "fractionalDigits" -> 2;
            case "format" -> String.format(Locale.ROOT, "%.2f", (double) arguments[0]);
            case "currencyNamePlural" -> "credits";
            case "currencyNameSingular" -> "credit";
            case "hasAccount", "createPlayerAccount" -> true;
            case "getBalance" -> balance(playerId(arguments));
            case "has" -> balance(playerId(arguments)) >= amount(arguments);
            case "withdrawPlayer" -> withdraw(playerId(arguments), amount(arguments));
            case "depositPlayer" -> deposit(playerId(arguments), amount(arguments));
            case "createBank", "deleteBank", "bankBalance", "bankHas", "bankWithdraw", "bankDeposit",
                    "isBankOwner", "isBankMember" -> new EconomyResponse(0D, 0D, ResponseType.NOT_IMPLEMENTED,
                            "banks are outside runtime provider/fault qualification");
            case "getBanks" -> List.of();
            case "toString" -> "QualificationControlledEconomy";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == arguments[0];
            default -> defaultValue(method.getReturnType());
        };
    }

    private EconomyResponse withdraw(UUID playerId, double amount) {
        if (mode.get() == Mode.THROW) throw new IllegalStateException("controlled Vault callback failure");
        double current = balance(playerId);
        if (mode.get() == Mode.FAIL) {
            return new EconomyResponse(0D, current, ResponseType.FAILURE, "controlled definitely-not-applied");
        }
        double replacement = current - amount;
        balances.put(playerId, replacement);
        withdrawals.incrementAndGet();
        double reported = mode.get() == Mode.UNCERTAIN ? amount + 1D : amount;
        return new EconomyResponse(reported, replacement, ResponseType.SUCCESS, "");
    }

    private EconomyResponse deposit(UUID playerId, double amount) {
        double replacement = balance(playerId) + amount;
        balances.put(playerId, replacement);
        deposits.incrementAndGet();
        return new EconomyResponse(amount, replacement, ResponseType.SUCCESS, "");
    }

    private static UUID playerId(Object[] arguments) {
        for (Object argument : arguments) {
            if (argument instanceof OfflinePlayer player) return player.getUniqueId();
        }
        for (Object argument : arguments) {
            if (argument instanceof String text) return UUID.nameUUIDFromBytes(text.getBytes(StandardCharsets.UTF_8));
        }
        throw new IllegalArgumentException("Vault callback did not contain a player");
    }

    private static double amount(Object[] arguments) {
        return Arrays.stream(arguments).filter(Double.class::isInstance).map(Double.class::cast)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Vault callback did not contain amount"));
    }

    private static Object defaultValue(Class<?> type) {
        if (type == void.class) return null;
        if (!type.isPrimitive()) return null;
        return Array.get(Array.newInstance(type, 1), 0);
    }

    private enum Mode {
        SUCCESS,
        FAIL,
        UNCERTAIN,
        THROW
    }
}
