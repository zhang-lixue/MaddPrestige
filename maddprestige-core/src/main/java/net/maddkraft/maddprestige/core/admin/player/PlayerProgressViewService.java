package net.maddkraft.maddprestige.core.admin.player;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.core.admin.OperationPreviewService;
import net.maddkraft.maddprestige.core.admin.PermissionSubject;

public final class PlayerProgressViewService {
    private final OperationPreviewService previews;

    public PlayerProgressViewService(OperationPreviewService previews) {
        this.previews = Objects.requireNonNull(previews, "preview service");
    }

    public CompletionStage<PlayerProgressView> view(PermissionSubject subject, UUID playerId) {
        return previews.simulatePrestige(subject, playerId)
                .thenApply(prestige -> new PlayerProgressView(playerId, prestige));
    }
}
