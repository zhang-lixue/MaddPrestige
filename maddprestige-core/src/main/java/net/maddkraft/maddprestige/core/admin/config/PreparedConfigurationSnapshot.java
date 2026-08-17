package net.maddkraft.maddprestige.core.admin.config;

import net.maddkraft.maddprestige.core.config.BackupMetadata;

public interface PreparedConfigurationSnapshot extends AutoCloseable {
    BackupMetadata backup();

    void activate();

    void restorePrevious();

    @Override
    void close();
}
