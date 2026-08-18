package net.maddkraft.maddprestige.api.provider;

import net.maddkraft.maddprestige.api.annotation.Stable;

/**
 * Service-manager declaration owned by the plugin that registered it. MaddPrestige derives the
 * namespace from Paper's attested service owner and never trusts caller-supplied owner strings.
 */
@Stable
public interface ProviderDeclaration {
    /**
     * Supplies immutable metadata outside registry locks under a bounded callback deadline.
     *
     * @param context non-null owner-attested callback context valid only until this call returns
     * @return non-null immutable provider metadata
     */
    ProviderMetadata metadata(ProviderCallContext context);

    /**
     * Returns the declaration's long-lived requirement callback implementation.
     *
     * @return non-null thread-safe callback implementation owned by this declaration
     */
    RequirementProvider requirements();

    /**
     * Associates the opaque lifecycle capability after successful activation.
     *
     * @param handle non-null generation-specific capability; a declaration must not transfer it to another owner
     */
    default void registered(ProviderRegistrationHandle handle) {
    }

    /**
     * Notifies the declaration after its exact registration has been invalidated.
     * Implementations must be non-blocking and tolerate repeated plugin lifecycle transitions.
     */
    default void unregistered() {
    }
}
