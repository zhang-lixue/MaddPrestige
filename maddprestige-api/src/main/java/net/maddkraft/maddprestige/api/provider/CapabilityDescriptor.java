package net.maddkraft.maddprestige.api.provider;

import java.util.Map;
import java.util.Objects;

public record CapabilityDescriptor(String id, String category, String description, Map<String, String> attributes) {
    public CapabilityDescriptor {
        id = Objects.requireNonNull(id, "capability ID");
        category = Objects.requireNonNull(category, "category");
        description = Objects.requireNonNull(description, "description");
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes"));
    }
}
