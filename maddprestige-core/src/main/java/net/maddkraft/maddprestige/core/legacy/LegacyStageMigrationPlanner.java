package net.maddkraft.maddprestige.core.legacy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.validation.ValidationFinding;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.api.validation.ValidationSeverity;
import net.maddkraft.maddprestige.core.config.BackupMetadata;
import net.maddkraft.maddprestige.core.stage.StageConfiguration;
import net.maddkraft.maddprestige.core.stage.StageDefinition;

public final class LegacyStageMigrationPlanner {
    public LegacyStageMigrationPlan plan(
            Collection<LegacyPlayerStageRecord> records,
            LegacyStageMappingManifest manifest,
            StageConfiguration targetConfiguration,
            boolean dryRun,
            Optional<BackupMetadata> backup) {
        ArrayList<ValidationFinding> findings = new ArrayList<>();
        Map<String, StageId> mappings = compileManifest(manifest, targetConfiguration, findings);
        LinkedHashMap<UUID, StageId> planned = new LinkedHashMap<>();
        for (LegacyPlayerStageRecord record : records) {
            StageId target = mappings.get(record.legacyValue());
            if (target == null) {
                findings.add(error("legacy.mapping.missing", "legacy.player-state." + record.playerId(),
                        "No explicit mapping exists for opaque legacy value '" + record.legacyValue() + "'.",
                        "Add one exact manifest entry; no mapping is inferred."));
            } else {
                planned.put(record.playerId(), target);
            }
        }
        if (!dryRun && backup.filter(BackupMetadata::verified).isEmpty()) {
            findings.add(error("legacy.backup.required", "legacy.backup",
                    "A verified backup is required before any future mutation-capable migration.",
                    "Create and verify a backup through the existing migration foundation."));
        }
        ValidationReport report = ValidationReport.of(findings);
        Map<UUID, StageId> safePlan = report.hasErrors() ? Map.of() : planned;
        return new LegacyStageMigrationPlan(manifest.revision(), dryRun, true, safePlan, report);
    }

    private static Map<String, StageId> compileManifest(
            LegacyStageMappingManifest manifest,
            StageConfiguration targetConfiguration,
            List<ValidationFinding> findings) {
        LinkedHashMap<String, LinkedHashSet<StageId>> targetsByValue = new LinkedHashMap<>();
        LinkedHashSet<String> invalidValues = new LinkedHashSet<>();
        for (LegacyStageMappingEntry entry : manifest.entries()) {
            targetsByValue.computeIfAbsent(entry.legacyValue(), ignored -> new LinkedHashSet<>())
                    .add(entry.targetStage());
            StageDefinition target = targetConfiguration.stages().get(entry.targetStage());
            if (target == null || !target.enabled() || !targetConfiguration.order().contains(entry.targetStage())) {
                invalidValues.add(entry.legacyValue());
                findings.add(error("legacy.mapping.target_invalid", "legacy.mapping." + entry.legacyValue(),
                        "Mapping target is missing, disabled, or unordered: " + entry.targetStage().value(),
                        "Choose an enabled ordered immutable stage ID."));
            }
        }
        LinkedHashMap<String, StageId> mappings = new LinkedHashMap<>();
        targetsByValue.forEach((legacyValue, targets) -> {
            if (targets.size() > 1) {
                findings.add(error("legacy.mapping.ambiguous", "legacy.mapping." + legacyValue,
                        "The same legacy value maps to multiple targets: "
                                + targets.stream().map(StageId::value).sorted().toList() + ".",
                        "Keep exactly one explicit target for each opaque legacy value."));
            } else if (!invalidValues.contains(legacyValue)) {
                mappings.put(legacyValue, targets.iterator().next());
            }
        });
        return mappings;
    }

    private static ValidationFinding error(String code, String path, String explanation, String remediation) {
        return new ValidationFinding(code, ValidationSeverity.ERROR, path, explanation,
                "The plan remains a deterministic read-only report and performs no production migration.", remediation);
    }
}
