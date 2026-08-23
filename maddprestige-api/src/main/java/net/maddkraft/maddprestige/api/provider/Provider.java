package net.maddkraft.maddprestige.api.provider;

import net.maddkraft.maddprestige.api.annotation.Experimental;

@Experimental
public interface Provider {
    ProviderDescriptor descriptor();

    ProviderHealth health();
}
