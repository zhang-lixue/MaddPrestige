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
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.provider.ProviderLifecycle;
import net.maddkraft.maddprestige.api.provider.ProviderSnapshot;

public final class ProviderRegistry {
    private final Map<ProviderId, RegisteredProvider> providers = new LinkedHashMap<>();
    private final Map<ProviderId, Long> lastGenerations = new LinkedHashMap<>();
    private final java.util.concurrent.CopyOnWriteArrayList<ProviderHealthListener> healthListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.concurrent.CopyOnWriteArrayList<ProviderLifecycleListener> lifecycleListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    public ProviderRegistration register(String callerIdentity, Provider provider) {
        Objects.requireNonNull(callerIdentity, "caller identity");
        Objects.requireNonNull(provider, "provider");
        ProviderDescriptor descriptor = Objects.requireNonNull(provider.descriptor(), "provider descriptor");
        ProviderHealth health = safeHealth(provider);
        if (!callerIdentity.equals(descriptor.ownerIdentity())) {
            throw new SecurityException("Provider owner identity does not match registering caller");
        }
        return registerAttested(callerIdentity, descriptor, provider, health);
    }

    /** Registers callback metadata that was obtained and validated outside the registry monitor. */
    public ProviderRegistration registerAttested(
            String attestedOwner,
            ProviderDescriptor descriptor,
            Provider provider,
            ProviderHealth initialHealth) {
        Objects.requireNonNull(attestedOwner, "attested owner");
        Objects.requireNonNull(descriptor, "provider descriptor");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(initialHealth, "initial health");
        if (!attestedOwner.equals(descriptor.ownerIdentity())) {
            throw new SecurityException("Provider descriptor owner is not the attested owner");
        }
        ProviderId id = descriptor.id();
        ProviderRegistration registration;
        synchronized (this) {
            if (providers.containsKey(id)) {
                throw new IllegalArgumentException("Provider is already registered: " + id.value());
            }
            long generation = Math.addExact(lastGenerations.getOrDefault(id, 0L), 1L);
            UUID token = UUID.randomUUID();
            providers.put(id, new RegisteredProvider(provider, descriptor, initialHealth, generation, token,
                    ActivationState.INACTIVE));
            lastGenerations.put(id, generation);
            registration = new ProviderRegistration(id, generation, token);
        }
        notifyLifecycle(id);
        return registration;
    }

    public void activate(ProviderRegistration registration) {
        boolean changed;
        synchronized (this) {
            RegisteredProvider current = requireCurrent(registration);
            changed = current.activation != ActivationState.ACTIVE;
            if (changed) {
                providers.put(registration.providerId(), current.withActivation(ActivationState.ACTIVE));
            }
        }
        if (changed) {
            notifyLifecycle(registration.providerId());
        }
    }

    public void deactivate(ProviderRegistration registration) {
        boolean changed;
        synchronized (this) {
            RegisteredProvider current = requireCurrent(registration);
            changed = current.activation != ActivationState.INACTIVE;
            if (changed) {
                providers.put(registration.providerId(), current.withActivation(ActivationState.INACTIVE));
            }
        }
        if (changed) {
            notifyLifecycle(registration.providerId());
        }
    }

    public void unregister(ProviderRegistration registration) {
        synchronized (this) {
            requireCurrent(registration);
            providers.remove(registration.providerId());
        }
        notifyLifecycle(registration.providerId());
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

    /** Refreshes callback-derived health outside the monitor, then atomically caches it if still current. */
    public Optional<ProviderHealth> refreshHealth(ProviderId id) {
        Objects.requireNonNull(id, "provider ID");
        RegisteredProvider observed;
        synchronized (this) {
            observed = providers.get(id);
        }
        if (observed == null) {
            return Optional.empty();
        }
        ProviderHealth refreshed = safeHealth(observed.provider);
        ProviderHealth previous = null;
        synchronized (this) {
            RegisteredProvider current = providers.get(id);
            if (current != null && current.generation == observed.generation && current.token.equals(observed.token)) {
                previous = current.health;
                providers.put(id, current.withHealth(refreshed));
            }
        }
        if (previous == null) {
            return Optional.empty();
        }
        if (!sameMeaning(previous, refreshed)) {
            for (ProviderHealthListener listener : healthListeners) {
                try {
                    listener.changed(id, previous, refreshed);
                } catch (RuntimeException | LinkageError ignored) {
                    // Observers cannot change registry state or another observer's delivery.
                }
            }
        }
        return Optional.of(refreshed);
    }

    private static boolean sameMeaning(ProviderHealth left, ProviderHealth right) {
        return left.state() == right.state() && left.code().equals(right.code())
                && left.reason().equals(right.reason());
    }

    /** Adds a cached-health observer and returns a removal capability. */
    public AutoCloseable addHealthListener(ProviderHealthListener listener) {
        ProviderHealthListener value = Objects.requireNonNull(listener, "health listener");
        healthListeners.add(value);
        return () -> healthListeners.remove(value);
    }

    /** Adds a registration/activation observer and returns a removal capability. */
    public AutoCloseable addLifecycleListener(ProviderLifecycleListener listener) {
        ProviderLifecycleListener value = Objects.requireNonNull(listener, "lifecycle listener");
        lifecycleListeners.add(value);
        return () -> lifecycleListeners.remove(value);
    }

    private void notifyLifecycle(ProviderId providerId) {
        for (ProviderLifecycleListener listener : lifecycleListeners) {
            try {
                listener.changed(providerId);
            } catch (RuntimeException | LinkageError ignored) {
                // Observers cannot change registry state or another observer's delivery.
            }
        }
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

    private static ProviderHealth safeHealth(Provider provider) {
        try {
            return Objects.requireNonNull(provider.health(), "provider health");
        } catch (RuntimeException | LinkageError failure) {
            return new ProviderHealth(ProviderHealthState.UNHEALTHY, "provider.health_callback_failed",
                    "Provider health callback failed safely", java.time.Instant.now());
        }
    }

    private record RegisteredProvider(
            Provider provider,
            ProviderDescriptor descriptor,
            ProviderHealth health,
            long generation,
            UUID token,
            ActivationState activation) {
        private boolean matches(ProviderRegistration registration) {
            return generation == registration.generation() && token.equals(registration.token());
        }

        private RegisteredProvider withActivation(ActivationState replacement) {
            return new RegisteredProvider(provider, descriptor, health, generation, token, replacement);
        }

        private RegisteredProvider withHealth(ProviderHealth replacement) {
            return new RegisteredProvider(provider, descriptor, replacement, generation, token, activation);
        }

        private ProviderSnapshot snapshot() {
            ProviderLifecycle lifecycle = activation == ActivationState.ACTIVE
                    ? ProviderLifecycle.RUNNING : ProviderLifecycle.REGISTERED;
            return new ProviderSnapshot(descriptor, lifecycle, activation, health, generation);
        }
    }
}
