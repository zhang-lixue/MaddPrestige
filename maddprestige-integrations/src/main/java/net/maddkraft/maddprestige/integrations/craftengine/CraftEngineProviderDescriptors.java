package net.maddkraft.maddprestige.integrations.craftengine;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.provider.CapabilityDescriptor;
import net.maddkraft.maddprestige.api.provider.DependencyDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;

public final class CraftEngineProviderDescriptors {
    public static final ProviderId METRIC = new ProviderId("craftengine_item_count");
    public static final ProviderId REWARD = new ProviderId("craftengine_item_reward");
    public static final String ITEM_ID = "item-id";
    private static final Pattern NAMESPACED_KEY =
            Pattern.compile("[a-z0-9._-]{1,64}:[a-z0-9/._-]{1,190}");

    private CraftEngineProviderDescriptors() {
    }

    static String requireItemId(String value) {
        if (value == null || !NAMESPACED_KEY.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "CraftEngine item ID must be a fully namespaced lowercase key");
        }
        return value;
    }

    static ProviderDescriptor descriptor(ProviderId id, String category, String detectedVersion) {
        return new ProviderDescriptor(id, "maddprestige", "phase7", detectedVersion,
                List.of(new DependencyDescriptor("CraftEngine", "[26.7.4,26.7.5)",
                        Optional.of(detectedVersion))),
                List.of(new CapabilityDescriptor(id.value(), category,
                        "CraftEngine exact custom-item " + category,
                        Map.of("thread", "server", "identity", "CraftEngineItems public API",
                                "inventory", "storage contents only"))));
    }
}
