package net.maddkraft.maddprestige.core.admin.setup;

import java.util.List;
import java.util.Objects;

public record SetupDiscovery(
        boolean activeConfigurationPresent,
        List<SetupProviderOption> providers,
        String externalGroupPolicy) {
    public SetupDiscovery {
        providers = List.copyOf(Objects.requireNonNull(providers, "providers"));
        externalGroupPolicy = Objects.requireNonNull(externalGroupPolicy, "external group policy");
    }
}
