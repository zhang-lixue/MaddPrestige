package gg.maddkraft.prestige.model;

import java.time.Instant;

public record Season(
        String id,
        int number,
        String displayName,
        SeasonStatus status,
        Instant startsAt,
        Instant endsAt
) {
    public Season withStatus(SeasonStatus newStatus) {
        return new Season(id, number, displayName, newStatus, startsAt, endsAt);
    }
}
