package net.maddkraft.maddprestige.core.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.provider.Provider;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProviderRegistryTest {
    @Test
    @DisplayName("[A50][A65] Provider owner, activation, and stale generations are enforced")
    void enforcesRegistrationIdentityAndGeneration() {
        ProviderRegistry registry = new ProviderRegistry();
        TestProvider provider = new TestProvider("test-owner");
        assertThrows(SecurityException.class, () -> registry.register("spoofed-owner", provider));

        ProviderRegistration first = registry.register("test-owner", provider);
        assertFalse(registry.acceptsEvent(first));
        registry.activate(first);
        assertTrue(registry.acceptsEvent(first));
        assertEquals(ProviderHealthState.AVAILABLE,
                registry.find(new ProviderId("fake_progress")).orElseThrow().health().state());
        registry.unregister(first);

        ProviderRegistration second = registry.register("test-owner", provider);
        assertEquals(first.generation() + 1, second.generation());
        assertFalse(registry.acceptsEvent(first));
    }

    private static final class TestProvider implements Provider {
        private final ProviderDescriptor descriptor;

        private TestProvider(String owner) {
            descriptor = new ProviderDescriptor(new ProviderId("fake_progress"), owner, "1", "1.0.0",
                    List.of(), List.of());
        }

        @Override
        public ProviderDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public ProviderHealth health() {
            return new ProviderHealth(ProviderHealthState.AVAILABLE, "provider.ready", "Ready for activation", Instant.EPOCH);
        }
    }
}
