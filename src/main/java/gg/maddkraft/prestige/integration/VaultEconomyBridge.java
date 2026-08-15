package gg.maddkraft.prestige.integration;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.NumberFormat;
import java.util.Locale;

public final class VaultEconomyBridge implements EconomyBridge {
    private final Economy economy;

    private VaultEconomyBridge(Economy economy) {
        this.economy = economy;
    }

    public static EconomyBridge create(JavaPlugin plugin, boolean enabled) {
        if (!enabled || plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return new UnavailableEconomyBridge();
        }
        RegisteredServiceProvider<Economy> registration = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null || registration.getProvider() == null) return new UnavailableEconomyBridge();
        return new VaultEconomyBridge(registration.getProvider());
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public double balance(OfflinePlayer player) {
        try {
            double balance = economy.getBalance(player);
            return Double.isFinite(balance) ? balance : 0.0;
        } catch (RuntimeException ignored) {
            return 0.0;
        }
    }

    @Override
    public TransactionResult withdraw(OfflinePlayer player, double amount) {
        if (!Double.isFinite(amount) || amount < 0.0) return TransactionResult.fail("invalid withdrawal amount");
        if (amount == 0.0) return TransactionResult.ok();
        try {
            EconomyResponse response = economy.withdrawPlayer(player, amount);
            return response.transactionSuccess() ? TransactionResult.ok() : TransactionResult.fail(response.errorMessage);
        } catch (RuntimeException exception) {
            return TransactionResult.fail("economy provider error: " + exception.getMessage());
        }
    }

    @Override
    public TransactionResult deposit(OfflinePlayer player, double amount) {
        if (!Double.isFinite(amount) || amount < 0.0) return TransactionResult.fail("invalid deposit amount");
        if (amount == 0.0) return TransactionResult.ok();
        try {
            EconomyResponse response = economy.depositPlayer(player, amount);
            return response.transactionSuccess() ? TransactionResult.ok() : TransactionResult.fail(response.errorMessage);
        } catch (RuntimeException exception) {
            return TransactionResult.fail("economy provider error: " + exception.getMessage());
        }
    }

    @Override
    public String format(double amount) {
        try {
            return economy.format(amount);
        } catch (RuntimeException ignored) {
            return NumberFormat.getCurrencyInstance(Locale.US).format(amount);
        }
    }

    private static final class UnavailableEconomyBridge implements EconomyBridge {
        @Override public boolean available() { return false; }
        @Override public double balance(OfflinePlayer player) { return 0.0; }
        @Override public TransactionResult withdraw(OfflinePlayer player, double amount) { return TransactionResult.fail("Vault economy unavailable"); }
        @Override public TransactionResult deposit(OfflinePlayer player, double amount) { return TransactionResult.fail("Vault economy unavailable"); }
        @Override public String format(double amount) { return String.format(Locale.US, "$%,.2f", amount); }
    }
}
