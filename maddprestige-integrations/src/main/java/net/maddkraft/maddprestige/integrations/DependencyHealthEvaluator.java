package net.maddkraft.maddprestige.integrations;

import java.time.Clock;
import java.util.Objects;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;

public final class DependencyHealthEvaluator {
    private final Clock clock;

    public DependencyHealthEvaluator(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ProviderHealth evaluate(
            boolean installed,
            boolean versionSupported,
            boolean configuredForUse,
            boolean dependencyHealthy) {
        if (!installed) {
            return health(ProviderHealthState.NOT_INSTALLED, "dependency.not_installed", "Dependency is not installed");
        }
        if (!versionSupported) {
            return health(ProviderHealthState.UNSUPPORTED, "dependency.unsupported", "Dependency version is not qualified");
        }
        if (!configuredForUse) {
            return health(ProviderHealthState.INACTIVE, "provider.inactive", "Integration is available but not configured");
        }
        if (!dependencyHealthy) {
            return health(ProviderHealthState.UNAVAILABLE, "dependency.unavailable", "Configured dependency is unavailable");
        }
        return health(ProviderHealthState.ACTIVE, "provider.active", "Configured integration is active");
    }

    private ProviderHealth health(ProviderHealthState state, String code, String reason) {
        return new ProviderHealth(state, code, reason, clock.instant());
    }
}
