package net.maddkraft.maddprestige.api.provider;

public interface Provider {
    ProviderDescriptor descriptor();

    ProviderHealth health();
}
