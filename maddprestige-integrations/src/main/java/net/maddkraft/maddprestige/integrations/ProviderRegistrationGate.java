package net.maddkraft.maddprestige.integrations;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.core.provider.ProviderRegistration;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;

/**
 * Exact-registration authority for Phase 5 external calls and persistent event mutation. The gate accepts only its
 * one bound generation while that registration is active and its operational health is usable.
 */
public final class ProviderRegistrationGate {
    private final ProviderRegistry registry;
    private final ProviderId providerId;
    private final MutableProviderHealth health;
    private final AtomicReference<ProviderRegistration> registration = new AtomicReference<>();

    public ProviderRegistrationGate(
            ProviderRegistry registry, ProviderId providerId, MutableProviderHealth health) {
        this.registry = Objects.requireNonNull(registry, "provider registry");
        this.providerId = Objects.requireNonNull(providerId, "provider ID");
        this.health = Objects.requireNonNull(health, "provider health");
    }

    /** Binds this gate once to the exact token and generation returned by registration. */
    public void bind(ProviderRegistration value) {
        Objects.requireNonNull(value, "provider registration");
        if (!providerId.equals(value.providerId())) {
            throw new IllegalArgumentException("Provider registration does not match this gate");
        }
        if (!registration.compareAndSet(null, value)) {
            throw new IllegalStateException("Provider registration gate is already bound");
        }
    }

    public boolean allowsUse() {
        ProviderRegistration current = registration.get();
        return current != null && health.isUsable() && registry.acceptsEvent(current);
    }

    public ProviderId providerId() {
        return providerId;
    }

    public boolean controls(ProviderId expectedProviderId, MutableProviderHealth expectedHealth) {
        return providerId.equals(expectedProviderId) && health == expectedHealth;
    }
}
