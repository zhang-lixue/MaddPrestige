package net.maddkraft.maddprestige.core.provider;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.provider.ActivationState;
import net.maddkraft.maddprestige.api.provider.Provider;
import net.maddkraft.maddprestige.api.provider.ProviderLifecycle;
import net.maddkraft.maddprestige.api.provider.ProviderSnapshot;

public final class ProviderRegistry {
    private final Map<ProviderId, RegisteredProvider> providers = new LinkedHashMap<>();
    private final Map<ProviderId, Long> lastGenerations = new LinkedHashMap<>();

    public synchronized ProviderRegistration register(String callerIdentity, Provider provider) {
        Objects.requireNonNull(callerIdentity, "caller identity");
        Objects.requireNonNull(provider, "provider");
        if (!callerIdentity.equals(provider.descriptor().ownerIdentity())) {
            throw new SecurityException("Provider owner identity does not match registering caller");
        }
        ProviderId id = provider.descriptor().id();
        if (providers.containsKey(id)) {
            throw new IllegalArgumentException("Provider is already registered: " + id.value());
        }
        long generation = Math.addExact(lastGenerations.getOrDefault(id, 0L), 1L);
        UUID token = UUID.randomUUID();
        providers.put(id, new RegisteredProvider(provider, generation, token, ActivationState.INACTIVE));
        lastGenerations.put(id, generation);
        return new ProviderRegistration(id, generation, token);
    }

    public synchronized void activate(ProviderRegistration registration) {
        RegisteredProvider current = requireCurrent(registration);
        providers.put(registration.providerId(), current.withActivation(ActivationState.ACTIVE));
    }

    public synchronized void deactivate(ProviderRegistration registration) {
        RegisteredProvider current = requireCurrent(registration);
        providers.put(registration.providerId(), current.withActivation(ActivationState.INACTIVE));
    }

    public synchronized void unregister(ProviderRegistration registration) {
        requireCurrent(registration);
        providers.remove(registration.providerId());
    }

    public synchronized Optional<ProviderSnapshot> find(ProviderId id) {
        return Optional.ofNullable(providers.get(id)).map(RegisteredProvider::snapshot);
    }

    public synchronized Optional<Provider> provider(ProviderId id) {
        Objects.requireNonNull(id, "provider ID");
        return Optional.ofNullable(providers.get(id)).map(RegisteredProvider::provider);
    }

    public synchronized Collection<ProviderSnapshot> snapshots() {
        return providers.values().stream().map(RegisteredProvider::snapshot).toList();
    }

    public synchronized boolean acceptsEvent(ProviderRegistration registration) {
        RegisteredProvider current = providers.get(registration.providerId());
        return current != null && current.matches(registration) && current.activation == ActivationState.ACTIVE;
    }

    private RegisteredProvider requireCurrent(ProviderRegistration registration) {
        Objects.requireNonNull(registration, "registration");
        RegisteredProvider current = providers.get(registration.providerId());
        if (current == null || !current.matches(registration)) {
            throw new IllegalStateException("Provider registration is absent or stale: " + registration.providerId().value());
        }
        return current;
    }

    private record RegisteredProvider(Provider provider, long generation, UUID token, ActivationState activation) {
        private boolean matches(ProviderRegistration registration) {
            return generation == registration.generation() && token.equals(registration.token());
        }

        private RegisteredProvider withActivation(ActivationState replacement) {
            return new RegisteredProvider(provider, generation, token, replacement);
        }

        private ProviderSnapshot snapshot() {
            ProviderLifecycle lifecycle = activation == ActivationState.ACTIVE
                    ? ProviderLifecycle.RUNNING : ProviderLifecycle.REGISTERED;
            return new ProviderSnapshot(provider.descriptor(), lifecycle, activation, provider.health(), generation);
        }
    }
}
