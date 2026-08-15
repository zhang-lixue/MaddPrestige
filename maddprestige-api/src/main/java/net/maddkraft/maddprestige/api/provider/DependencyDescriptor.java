package net.maddkraft.maddprestige.api.provider;

import java.util.Objects;
import java.util.Optional;

public record DependencyDescriptor(String identity, String supportedRange, Optional<String> detectedVersion) {
    public DependencyDescriptor {
        identity = Objects.requireNonNull(identity, "identity");
        supportedRange = Objects.requireNonNull(supportedRange, "supported range");
        detectedVersion = Objects.requireNonNull(detectedVersion, "detected version");
    }
}
