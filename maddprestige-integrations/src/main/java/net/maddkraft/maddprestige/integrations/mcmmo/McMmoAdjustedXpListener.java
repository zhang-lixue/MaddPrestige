package net.maddkraft.maddprestige.integrations.mcmmo;

import com.gmail.nossr50.events.experience.McMMOPlayerXpGainEvent;
import java.time.Clock;
import java.util.Objects;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.core.manual.ManualMetricHandle;
import net.maddkraft.maddprestige.integrations.ProviderRegistrationGate;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Captures post-adjustment mcMMO XP without resetting or editing mcMMO-owned progression. */
public final class McMmoAdjustedXpListener implements Listener {
    private final ManualMetricHandle totalXp;
    private final ProviderRegistrationGate registrationGate;
    private final Clock clock;

    public McMmoAdjustedXpListener(
            ManualMetricHandle totalXp, ProviderRegistrationGate registrationGate, Clock clock) {
        this.totalXp = Objects.requireNonNull(totalXp, "total XP handle");
        this.registrationGate = Objects.requireNonNull(registrationGate, "registration gate");
        if (!McMmoMetricProvider.PROVIDER_ID.equals(registrationGate.providerId())) {
            throw new IllegalArgumentException("mcMMO listener requires the mcMMO registration gate");
        }
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onXpGain(McMMOPlayerXpGainEvent event) {
        float adjustedXp = event.getRawXpGained();
        if (!registrationGate.allowsUse() || event.isCancelled() || !Float.isFinite(adjustedXp)
                || adjustedXp <= 0) {
            return;
        }
        totalXp.increment(event.getPlayer().getUniqueId(), MetricValue.decimal(Float.toString(adjustedXp)),
                "mcmmo:adjusted-xp:" + event.getSkill().name(), clock.instant());
    }
}
