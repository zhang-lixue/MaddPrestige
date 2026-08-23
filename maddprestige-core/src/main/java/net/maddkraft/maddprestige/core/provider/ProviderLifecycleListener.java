package net.maddkraft.maddprestige.core.provider;

import net.maddkraft.maddprestige.api.id.ProviderId;

/** Internal registry-lifecycle observer; callbacks are always invoked outside registry monitors. */
@FunctionalInterface
public interface ProviderLifecycleListener {
    void changed(ProviderId providerId);
}
