package net.maddkraft.maddprestige.api.provider;

import net.maddkraft.maddprestige.api.annotation.Stable;

/** Stable coarse lifecycle and diagnostic state for a provider capability. */
@Stable
public enum ProviderHealthState {
    /** The optional backing plugin or service is not installed. */
    NOT_INSTALLED,

    /** The provider is reachable and healthy but not currently selected by canonical configuration. */
    AVAILABLE,

    /** The provider is healthy and active for the authoritative configuration. */
    ACTIVE,

    /** The provider is installed but intentionally inactive. */
    INACTIVE,

    /** The provider remains usable in a reduced state and requires operator attention. */
    DEGRADED,

    /** The detected implementation version or capability is unsupported. */
    UNSUPPORTED,

    /** A required dependency, binding, or runtime prerequisite is temporarily unavailable. */
    UNAVAILABLE,

    /** Validation or callback health failed and the provider is fenced closed. */
    UNHEALTHY
}
