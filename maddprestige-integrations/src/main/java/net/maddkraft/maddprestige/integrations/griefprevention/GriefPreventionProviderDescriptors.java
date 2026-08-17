package net.maddkraft.maddprestige.integrations.griefprevention;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.provider.CapabilityDescriptor;
import net.maddkraft.maddprestige.api.provider.DependencyDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;

public final class GriefPreventionProviderDescriptors {
    public static final String OWNER = "maddprestige";
    public static final ProviderId METRIC = new ProviderId("griefprevention_claims");
    public static final ProviderId REWARD = new ProviderId("griefprevention_claim_blocks_reward");

    private GriefPreventionProviderDescriptors() {
    }

    static ProviderDescriptor descriptor(ProviderId id, String category, String detectedVersion) {
        return new ProviderDescriptor(id, OWNER, "phase7", detectedVersion,
                List.of(new DependencyDescriptor("GriefPrevention", "[16.18.7,16.18.8)",
                        Optional.of(detectedVersion))),
                List.of(new CapabilityDescriptor(id.value(), category,
                        "GriefPrevention " + category + " capability",
                        Map.of("thread", "server", "persistence", "GriefPrevention DataStore"))));
    }
}
