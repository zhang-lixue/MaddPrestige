package net.maddkraft.maddprestige.core.provider;

import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;

/** Internal cached-health observer; callbacks are always invoked outside registry monitors. */
@FunctionalInterface
public interface ProviderHealthListener {
    void changed(ProviderId providerId, ProviderHealth previous, ProviderHealth current);
}
