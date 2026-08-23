package net.maddkraft.maddprestige.platform.paper.bootstrap;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationApplicationStatus;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationHistoryStore;
import net.maddkraft.maddprestige.core.admin.config.StoredConfigurationRevision;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.persistence.admin.AtomicConfigurationFileStore;

/** Restores only an exact pointer/history/document tuple; seed files never become runtime authority here. */
final class StartupConfigurationLoader {
    private StartupConfigurationLoader() {
    }

    static Optional<StoredConfigurationRevision> load(
            AtomicConfigurationFileStore snapshots,
            ConfigurationHistoryStore history) {
        Objects.requireNonNull(snapshots, "configuration snapshots");
        Objects.requireNonNull(history, "configuration history");
        var pointer = snapshots.currentRevision();
        if (pointer.isEmpty()) {
            return Optional.empty();
        }
        StoredConfigurationRevision stored = history.find(pointer.orElseThrow()).orElseThrow(() ->
                new IllegalStateException("Active configuration pointer has no durable history owner"));
        if (stored.status() != ConfigurationApplicationStatus.APPLIED) {
            throw new IllegalStateException("Active configuration pointer does not reference APPLIED history");
        }
        Map<String, String> documents = snapshots.activeDocuments().orElseThrow(() ->
                new IllegalStateException("Active configuration pointer has no checksum-verified documents"));
        if (!stored.compiled().documents().equals(documents)
                || !stored.compiled().contentHash().equals(RevisionHasher.hashDocuments(documents))) {
            throw new IllegalStateException("Active configuration pointer, history, and documents are incoherent");
        }
        return Optional.of(stored);
    }
}
