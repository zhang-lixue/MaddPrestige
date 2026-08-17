package net.maddkraft.maddprestige.core.admin.config;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.core.config.ContentHash;
import net.maddkraft.maddprestige.core.stage.StageChangeImpact;

public record ConfigurationPreview(
        UUID draftId,
        long draftVersion,
        Optional<ConfigRevisionId> baseRevision,
        Optional<ConfigRevisionId> rollbackSource,
        ContentHash candidateHash,
        Optional<ContentHash> stageRemapSeal,
        List<String> changedDocuments,
        ValidationReport validation,
        StageChangeImpact stageImpact,
        boolean stale) {
    public ConfigurationPreview {
        draftId = Objects.requireNonNull(draftId, "draft ID");
        if (draftVersion < 1) {
            throw new IllegalArgumentException("Draft version must be positive");
        }
        baseRevision = Objects.requireNonNull(baseRevision, "base revision");
        rollbackSource = Objects.requireNonNull(rollbackSource, "rollback source");
        candidateHash = Objects.requireNonNull(candidateHash, "candidate hash");
        stageRemapSeal = Objects.requireNonNull(stageRemapSeal, "stage remap seal");
        changedDocuments = List.copyOf(Objects.requireNonNull(changedDocuments, "changed documents"));
        validation = Objects.requireNonNull(validation, "validation");
        stageImpact = Objects.requireNonNull(stageImpact, "stage impact");
    }
}
