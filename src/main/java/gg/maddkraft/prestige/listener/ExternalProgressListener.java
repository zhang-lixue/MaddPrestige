package gg.maddkraft.prestige.listener;

import gg.maddkraft.prestige.api.MaddPrestigeProgressEvent;
import gg.maddkraft.prestige.service.LedgerService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class ExternalProgressListener implements Listener {
    private final LedgerService ledger;

    public ExternalProgressListener(LedgerService ledger) {
        this.ledger = ledger;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProgress(MaddPrestigeProgressEvent event) {
        if (!Double.isFinite(event.getAmount()) || event.getAmount() <= 0.0) return;
        switch (event.getType()) {
            case SERVER_EARNINGS -> ledger.addServerEarnings(event.getPlayer().getUniqueId(), event.getAmount(), event.getSource());
            case MCMMO_XP -> ledger.addMcMmoXp(event.getPlayer().getUniqueId(), Math.round(event.getAmount()));
            case RABBIT_HOLE -> ledger.addRabbitHole(event.getPlayer().getUniqueId(), (int) Math.round(event.getAmount()));
            case DECREE_OBJECTIVE -> ledger.addDecreeObjective(event.getPlayer().getUniqueId(), (int) Math.round(event.getAmount()));
            case BOSS -> ledger.addBoss(event.getPlayer().getUniqueId(), (int) Math.round(event.getAmount()));
        }
    }
}
