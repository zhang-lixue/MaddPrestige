package net.maddkraft.maddprestige.integrations;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.provider.ActivationState;
import net.maddkraft.maddprestige.api.provider.Provider;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.core.provider.ProviderRegistration;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.integrations.config.PhaseFiveIntegrationPlan;

/** Owns one optional dependency's registry bindings without making dormant providers critical. */
public final class IntegrationProviderLifecycle {
    private final ProviderRegistry registry;
    private final String ownerIdentity;
    private final Map<ProviderId, Binding> bindings = new LinkedHashMap<>();

    public IntegrationProviderLifecycle(ProviderRegistry registry, String ownerIdentity) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.ownerIdentity = Objects.requireNonNull(ownerIdentity, "owner identity");
    }

    public synchronized void discover(List<ManagedProvider> providers) {
        if (!bindings.isEmpty()) {
            throw new IllegalStateException("Integration providers are already discovered");
        }
        ArrayList<Binding> added = new ArrayList<>();
        try {
            for (ManagedProvider managed : List.copyOf(providers)) {
                ProviderRegistration registration = registry.register(ownerIdentity, managed.provider());
                Binding binding = new Binding(managed, registration);
                bindings.put(registration.providerId(), binding);
                added.add(binding);
                managed.registrationGate().bind(registration);
            }
        } catch (RuntimeException exception) {
            rollback(added);
            throw exception;
        }
    }

    /** Applies one validated configuration plan as the exact desired active-provider set. */
    public synchronized void reconcile(PhaseFiveIntegrationPlan plan) {
        Objects.requireNonNull(plan, "integration plan");
        reconcileActiveProviders(plan.reachableProviders());
    }

    /**
     * Reconciles this lifecycle's discovered bindings against the exact desired set. Existing desired
     * bindings retain their registration and generation; bindings removed from the set become dormant.
     */
    public synchronized void reconcileActiveProviders(Set<ProviderId> desiredProviders) {
        Set<ProviderId> desired = Set.copyOf(Objects.requireNonNull(desiredProviders, "desired providers"));
        for (Binding binding : bindings.values()) {
            ProviderId id = binding.registration().providerId();
            ActivationState current = registry.find(id).orElseThrow(() -> new IllegalStateException(
                    "Integration provider registration is absent: " + id.value())).activation();
            boolean desiredActive = desired.contains(id);
            if (desiredActive && current != ActivationState.ACTIVE) {
                registry.activate(binding.registration());
            } else if (!desiredActive && current == ActivationState.ACTIVE) {
                registry.deactivate(binding.registration());
            }
        }
    }

    public synchronized void deactivateAll(String reason) {
        Objects.requireNonNull(reason, "reason");
        for (Binding binding : bindings.values()) {
            registry.deactivate(binding.registration());
        }
    }

    /** Marks an exact still-current binding healthy only after an explicit successful probe or reacquisition. */
    public synchronized void dependencyRecovered(ProviderRegistration registration, String reason) {
        Objects.requireNonNull(registration, "provider registration");
        Objects.requireNonNull(reason, "reason");
        Binding binding = bindings.get(registration.providerId());
        if (binding == null || !binding.registration().equals(registration)) {
            throw new IllegalStateException("Provider registration is absent or stale: "
                    + registration.providerId().value());
        }
        if (registry.find(registration.providerId()).filter(snapshot ->
                snapshot.generation() == registration.generation()).isEmpty()) {
            throw new IllegalStateException("Provider registration is absent or stale: "
                    + registration.providerId().value());
        }
        binding.managed().health().transition(ProviderHealthState.AVAILABLE,
                "integration.dependency_recovered", reason);
    }

    public synchronized void dependencyUnavailable(String reason) {
        for (Binding binding : List.copyOf(bindings.values())) {
            binding.managed().health().transition(ProviderHealthState.UNAVAILABLE,
                    "integration.dependency_unavailable", reason);
            registry.unregister(binding.registration());
        }
        bindings.clear();
    }

    public synchronized void rebind(List<ManagedProvider> providers, Set<ProviderId> referenced) {
        dependencyUnavailable("Dependency binding was replaced");
        discover(providers);
        reconcileActiveProviders(referenced);
    }

    public synchronized Map<ProviderId, ProviderRegistration> registrations() {
        LinkedHashMap<ProviderId, ProviderRegistration> result = new LinkedHashMap<>();
        bindings.forEach((id, binding) -> result.put(id, binding.registration()));
        return Map.copyOf(result);
    }

    private void rollback(List<Binding> added) {
        for (Binding binding : added.reversed()) {
            registry.unregister(binding.registration());
            bindings.remove(binding.registration().providerId());
        }
    }

    public record ManagedProvider(
            Provider provider, MutableProviderHealth health, ProviderRegistrationGate registrationGate) {
        public ManagedProvider {
            provider = Objects.requireNonNull(provider, "provider");
            health = Objects.requireNonNull(health, "health");
            registrationGate = Objects.requireNonNull(registrationGate, "registration gate");
            if (!registrationGate.controls(provider.descriptor().id(), health)) {
                throw new IllegalArgumentException("Registration gate must control the managed provider and health");
            }
        }
    }

    private record Binding(ManagedProvider managed, ProviderRegistration registration) {
    }
}
