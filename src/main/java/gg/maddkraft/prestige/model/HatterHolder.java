package gg.maddkraft.prestige.model;

import java.time.Instant;
import java.util.UUID;

public record HatterHolder(UUID playerId, String contestId, String hatItemId, Instant since) {
}
