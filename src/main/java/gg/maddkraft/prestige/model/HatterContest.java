package gg.maddkraft.prestige.model;

import java.time.Instant;
import java.util.UUID;

public record HatterContest(
        String id,
        String seasonId,
        ContestMetric metric,
        Instant startsAt,
        Instant endsAt,
        int minimumPrestige,
        String status,
        UUID winnerId
) {
    public boolean activeAt(Instant now) {
        return "ACTIVE".equals(status) && !now.isBefore(startsAt) && now.isBefore(endsAt);
    }
}
