package net.maddkraft.maddprestige.core.legacy;

import java.util.List;
import java.util.Objects;

public record LegacyStageMappingManifest(String revision, List<LegacyStageMappingEntry> entries) {
    public LegacyStageMappingManifest {
        revision = Objects.requireNonNull(revision, "manifest revision");
        entries = List.copyOf(Objects.requireNonNull(entries, "mapping entries"));
        if (revision.isBlank()) {
            throw new IllegalArgumentException("Mapping manifest revision cannot be blank");
        }
    }
}
