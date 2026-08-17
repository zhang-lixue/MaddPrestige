package net.maddkraft.maddprestige.core.admin.ui;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.StageId;

public record GuiAction(
        UUID actionId,
        GuiActionKind kind,
        String label,
        String requiredPermission,
        boolean mutating,
        Optional<ConfigRevisionId> expectedConfigRevision,
        Optional<UUID> targetPlayer,
        Optional<StageId> targetStage,
        Optional<StageId> replacementStage,
        Optional<GuiMutationContext> mutationContext) {
    public GuiAction {
        actionId = Objects.requireNonNull(actionId, "action ID");
        kind = Objects.requireNonNull(kind, "kind");
        label = Objects.requireNonNull(label, "label");
        requiredPermission = Objects.requireNonNull(requiredPermission, "required permission");
        expectedConfigRevision = Objects.requireNonNull(expectedConfigRevision, "expected revision");
        targetPlayer = Objects.requireNonNull(targetPlayer, "target player");
        targetStage = Objects.requireNonNull(targetStage, "target stage");
        replacementStage = Objects.requireNonNull(replacementStage, "replacement stage");
        mutationContext = Objects.requireNonNull(mutationContext, "mutation context");
        if (mutating && expectedConfigRevision.isEmpty()) {
            throw new IllegalArgumentException("Mutating GUI action requires an expected configuration revision");
        }
    }

    public GuiAction(
            UUID actionId,
            GuiActionKind kind,
            String label,
            String requiredPermission,
            boolean mutating,
            Optional<ConfigRevisionId> expectedConfigRevision,
            Optional<UUID> targetPlayer,
            Optional<StageId> targetStage,
            Optional<StageId> replacementStage) {
        this(actionId, kind, label, requiredPermission, mutating, expectedConfigRevision, targetPlayer, targetStage,
                replacementStage, Optional.empty());
    }
}
