package net.maddkraft.maddprestige.core.manual;

import java.util.Objects;

/** Result of the trusted bootstrap boundary: publish the provider, retain the owner capability. */
public record ManualProgressBootstrap(ManualProgressProvider provider, ManualProgressOwner owner) {
    public ManualProgressBootstrap {
        provider = Objects.requireNonNull(provider, "provider");
        owner = Objects.requireNonNull(owner, "owner capability");
    }
}
