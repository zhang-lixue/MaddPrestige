package net.maddkraft.maddprestige.core.legacy;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.validation.ValidationReport;

public record LegacyStageMigrationPlan(
        String manifestRevision,
        boolean dryRun,
        boolean verifiedBackupRequiredBeforeMutation,
        Map<UUID, StageId> plannedPlayerMappings,
        ValidationReport report) {
    public LegacyStageMigrationPlan {
        manifestRevision = Objects.requireNonNull(manifestRevision, "manifest revision");
        plannedPlayerMappings = Map.copyOf(Objects.requireNonNull(plannedPlayerMappings, "planned mappings"));
        report = Objects.requireNonNull(report, "report");
        if (report.hasErrors() && !plannedPlayerMappings.isEmpty()) {
            throw new IllegalArgumentException("An invalid legacy plan cannot expose executable-looking mappings");
        }
    }

    public boolean canProceed() {
        return !report.hasErrors();
    }
}
