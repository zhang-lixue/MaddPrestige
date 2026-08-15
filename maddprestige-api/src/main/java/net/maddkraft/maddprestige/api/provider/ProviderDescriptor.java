package net.maddkraft.maddprestige.api.provider;

import java.util.List;
import java.util.Objects;
import net.maddkraft.maddprestige.api.id.ProviderId;

public record ProviderDescriptor(
        ProviderId id,
        String ownerIdentity,
        String apiVersion,
        String implementationVersion,
        List<DependencyDescriptor> dependencies,
        List<CapabilityDescriptor> capabilities) {
    public ProviderDescriptor {
        id = Objects.requireNonNull(id, "provider ID");
        ownerIdentity = Objects.requireNonNull(ownerIdentity, "owner identity");
        apiVersion = Objects.requireNonNull(apiVersion, "API version");
        implementationVersion = Objects.requireNonNull(implementationVersion, "implementation version");
        dependencies = List.copyOf(Objects.requireNonNull(dependencies, "dependencies"));
        capabilities = List.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
    }
}
