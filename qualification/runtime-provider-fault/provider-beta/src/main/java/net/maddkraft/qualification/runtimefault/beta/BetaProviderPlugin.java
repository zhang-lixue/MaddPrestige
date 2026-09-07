package net.maddkraft.qualification.runtimefault.beta;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
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
import net.maddkraft.maddprestige.api.provider.ProviderMetricRequest;
import net.maddkraft.maddprestige.api.provider.ProviderMetricResult;
import net.maddkraft.maddprestige.api.provider.ProviderRegistrationHandle;
import net.maddkraft.qualification.runtimefault.provider.ProviderFixtureSupport;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/** Second independently owned Stable provider for runtime provider/fault isolation qualification. */
public final class BetaProviderPlugin extends JavaPlugin {
    private static final ProviderFixtureSupport.Profile PROFILE = new ProviderFixtureSupport.Profile(
            "qualification_beta", "Beta", "beta", "qualification.provider.beta", "1.0.0", "tokens",
            List.of("tokens", "online_only"));

    private final AtomicReference<Mode> mode = new AtomicReference<>(Mode.HEALTHY);
    private final AtomicInteger reads = new AtomicInteger();
    private ProviderFixtureSupport.Registration registration;
    private ProviderFixtureSupport.Commands<Mode> commands;

    @Override
    public void onEnable() {
        registration = new ProviderFixtureSupport.Registration(this, "RUNTIME-QUALIFICATION-BETA", this::declaration);
        commands = new ProviderFixtureSupport.Commands<>(this, "qualificationbeta", "RUNTIME-QUALIFICATION-BETA",
                Mode.class, mode, replacement -> getLogger().info("RUNTIME-QUALIFICATION-BETA mode=" + replacement));
        registration.enable();
        getLogger().info("RUNTIME-QUALIFICATION-BETA enabled after MaddPrestige startup");
    }

    @Override
    public void onDisable() {
        registration.disable();
        getLogger().info("RUNTIME-QUALIFICATION-BETA disabled reads=" + reads.get());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        return commands.execute(sender, command, arguments, this::providerCommand);
    }

    private boolean providerCommand(String action, CommandSender sender) {
        switch (action) {
            case "unregister" -> registration.requireHandle("Beta provider handle is absent").unregister();
            case "rebind" -> registration.rebind();
            case "status" -> sender.sendMessage("mode=" + mode.get() + " reads=" + reads.get()
                    + " handle=" + registration.hasHandle());
            default -> {
                return false;
            }
        }
        return true;
    }

    private ProviderDeclaration declaration() {
        return ProviderFixtureSupport.declaration(PROFILE, this::read, this::registered, this::unregistered);
    }

    private void registered(ProviderRegistrationHandle providerHandle) {
        registration.registered(providerHandle);
        getLogger().info("RUNTIME-QUALIFICATION-BETA registered id=" + providerHandle.providerId().value());
    }

    private void unregistered() {
        getLogger().info("RUNTIME-QUALIFICATION-BETA unregistered callback");
    }

    private CompletionStage<Map<ProviderMetricRequest, ProviderMetricResult>> read(
            ProviderCallContext context,
            UUID playerId,
            List<ProviderMetricRequest> queries) {
        PROFILE.verifyReadContext(context);
        reads.incrementAndGet();
        Mode current = mode.get();
        if (current == Mode.THROW) throw new IllegalStateException("controlled Beta runtime failure");
        if (current == Mode.LINKAGE) throw new NoClassDefFoundError("controlled-beta-linkage");
        LinkedHashMap<ProviderMetricRequest, ProviderMetricResult> result = new LinkedHashMap<>();
        for (ProviderMetricRequest query : queries) {
            boolean unavailable = current == Mode.UNAVAILABLE
                    || current == Mode.ONLINE_ONLY && !Bukkit.getOfflinePlayer(playerId).isOnline();
            result.put(query, unavailable ? ProviderMetricResult.unavailable(Instant.now(),
                    "qualification.beta.unavailable", "qualification.beta.unavailable", Map.of())
                    : ProviderMetricResult.available(MetricValue.count(10), Instant.now()));
        }
        return CompletableFuture.completedFuture(result);
    }

    private enum Mode {
        HEALTHY,
        UNAVAILABLE,
        ONLINE_ONLY,
        THROW,
        LINKAGE
    }
}
