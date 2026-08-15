package net.maddkraft.maddprestige.platform.paper;

import org.bukkit.Bukkit;

public final class PaperThreadGuard {
    private PaperThreadGuard() {
    }

    public static void requireServerThread(String operation) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(operation + " requires Paper's server thread");
        }
    }

    public static void requireAsyncThread(String operation) {
        if (Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(operation + " must not block Paper's server thread");
        }
    }
}
