package net.maddkraft.maddprestige.testkit;

import java.time.Clock;
import java.util.List;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.provider.CapabilityDescriptor;
import net.maddkraft.maddprestige.api.provider.Provider;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;

public class FakeProvider implements Provider {
    private final ProviderDescriptor descriptor;
    private final ProviderHealthSimulator health;

    public FakeProvider(ProviderId id, String category, List<CapabilityDescriptor> capabilities) {
        this.descriptor = new ProviderDescriptor(id, "maddprestige-testkit", "1", "fake-1", List.of(), capabilities);
        this.health = new ProviderHealthSimulator(Clock.systemUTC(), ProviderHealthState.AVAILABLE);
    }

    @Override
    public ProviderDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public ProviderHealth health() {
        return health.current();
    }

    public ProviderHealthSimulator healthSimulator() {
        return health;
    }
}
