package gg.maddkraft.prestige.integration;

import org.bukkit.OfflinePlayer;

public interface EconomyBridge {
    boolean available();

    double balance(OfflinePlayer player);

    TransactionResult withdraw(OfflinePlayer player, double amount);

    TransactionResult deposit(OfflinePlayer player, double amount);

    String format(double amount);

    record TransactionResult(boolean successful, String error) {
        public static TransactionResult ok() { return new TransactionResult(true, ""); }
        public static TransactionResult fail(String error) { return new TransactionResult(false, error); }
    }
}
