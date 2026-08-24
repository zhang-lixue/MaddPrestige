package net.maddkraft.qualification.phase8e.beta;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.metric.MetricReadMode;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.provider.ProviderCallContext;
import net.maddkraft.maddprestige.api.provider.ProviderDeclaration;
import net.maddkraft.maddprestige.api.provider.ProviderMetadata;
import net.maddkraft.maddprestige.api.provider.ProviderMetricDefinition;
import net.maddkraft.maddprestige.api.provider.ProviderMetricRequest;
import net.maddkraft.maddprestige.api.provider.ProviderMetricResult;
import net.maddkraft.maddprestige.api.provider.ProviderRegistrationHandle;
import net.maddkraft.qualification.phase8e.provider.Phase8EProviderSupport;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/** Second independently owned Stable provider for Phase 8E isolation qualification. */
public final class Phase8EBetaProvider extends JavaPlugin {
    private final AtomicReference<Mode> mode = new AtomicReference<>(Mode.HEALTHY);
    private final AtomicInteger reads = new AtomicInteger();
    private Phase8EProviderSupport.Registration registration;

    @Override
    public void onEnable() {
        registration = new Phase8EProviderSupport.Registration(this, "PHASE8E-BETA", this::declaration);
        registration.enable();
        getLogger().info("PHASE8E-BETA enabled after MaddPrestige startup");
    }

    @Override
    public void onDisable() {
        registration.disable();
        getLogger().info("PHASE8E-BETA disabled reads=" + reads.get());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!command.getName().equalsIgnoreCase("phase8ebeta") || arguments.length == 0) return false;
        try {
            switch (arguments[0].toLowerCase(Locale.ROOT)) {
                case "mode" -> {
                    if (arguments.length != 2) return false;
                    Mode replacement = Mode.valueOf(arguments[1].toUpperCase(Locale.ROOT));
                    mode.set(replacement);
                    getLogger().info("PHASE8E-BETA mode=" + replacement);
                }
                case "unregister" -> registration.requireHandle("Beta provider handle is absent").unregister();
                case "rebind" -> registration.rebind();
                case "status" -> sender.sendMessage("mode=" + mode.get() + " reads=" + reads.get()
                        + " handle=" + registration.hasHandle());
                default -> {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException failure) {
            getLogger().log(java.util.logging.Level.SEVERE, "PHASE8E-BETA command failed", failure);
            return false;
        }
    }

    private ProviderDeclaration declaration() {
        return Phase8EProviderSupport.declaration(this::metadata, this::read, this::registered, this::unregistered);
    }

    private ProviderMetadata metadata(ProviderCallContext context) {
        verifyContext(context, false);
        return new ProviderMetadata("beta", "phase8e.provider.beta", "1.0.0", List.of(
                definition("tokens"), definition("online_only")));
    }

    private void registered(ProviderRegistrationHandle providerHandle) {
        registration.registered(providerHandle);
        getLogger().info("PHASE8E-BETA registered id=" + providerHandle.providerId().value());
    }

    private void unregistered() {
        getLogger().info("PHASE8E-BETA unregistered callback");
    }

    private CompletionStage<Map<ProviderMetricRequest, ProviderMetricResult>> read(
            ProviderCallContext context,
            UUID playerId,
            List<ProviderMetricRequest> queries) {
        verifyContext(context, true);
        reads.incrementAndGet();
        Mode current = mode.get();
        if (current == Mode.THROW) throw new IllegalStateException("controlled Beta runtime failure");
        if (current == Mode.LINKAGE) throw new NoClassDefFoundError("controlled-beta-linkage");
        LinkedHashMap<ProviderMetricRequest, ProviderMetricResult> result = new LinkedHashMap<>();
        for (ProviderMetricRequest query : queries) {
            boolean unavailable = current == Mode.UNAVAILABLE
                    || current == Mode.ONLINE_ONLY && !Bukkit.getOfflinePlayer(playerId).isOnline();
            result.put(query, unavailable ? ProviderMetricResult.unavailable(Instant.now(),
                    "phase8e.beta.unavailable", "phase8e.beta.unavailable", Map.of())
                    : ProviderMetricResult.available(MetricValue.count(10), Instant.now()));
        }
        return CompletableFuture.completedFuture(result);
    }

    private static ProviderMetricDefinition definition(String id) {
        return Phase8EProviderSupport.countMetric(id, "tokens");
    }

    private static void verifyContext(ProviderCallContext context, boolean identified) {
        Phase8EProviderSupport.verifyContext(context, identified, "phase8e_beta", "Beta");
    }

    private enum Mode {
        HEALTHY,
        UNAVAILABLE,
        ONLINE_ONLY,
        THROW,
        LINKAGE
    }
}
