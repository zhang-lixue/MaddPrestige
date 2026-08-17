package net.maddkraft.maddprestige.core.admin.player;

import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.core.admin.OperationPreview;

/** Immutable player-facing projection assembled only from canonical authorization previews. */
public record PlayerProgressView(
        UUID playerId,
        OperationPreview rankUp,
        OperationPreview prestige) {
    public PlayerProgressView {
        playerId = Objects.requireNonNull(playerId, "player ID");
        rankUp = Objects.requireNonNull(rankUp, "rank-up preview");
        prestige = Objects.requireNonNull(prestige, "Prestige preview");
    }
}
