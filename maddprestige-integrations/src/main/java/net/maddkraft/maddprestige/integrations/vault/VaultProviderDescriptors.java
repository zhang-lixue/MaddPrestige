package net.maddkraft.maddprestige.integrations.vault;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.provider.CapabilityDescriptor;
import net.maddkraft.maddprestige.api.provider.DependencyDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;

final class VaultProviderDescriptors {
    static final String OWNER = "maddprestige";
    static final ProviderId COST = new ProviderId("vault_economy_cost");
    static final ProviderId REWARD = new ProviderId("vault_economy_reward");
    static final ProviderId BALANCE = new ProviderId("vault_balance");

    private VaultProviderDescriptors() {
    }

    static ProviderDescriptor descriptor(ProviderId id, String category, String implementationVersion) {
        return new ProviderDescriptor(id, OWNER, "phase5", implementationVersion,
                List.of(new DependencyDescriptor("Vault", "[1.7,3)", Optional.empty())),
                List.of(new CapabilityDescriptor(id.value(), category,
                        "Vault economy " + category + " capability", Map.of("thread", "server"))));
    }
}
