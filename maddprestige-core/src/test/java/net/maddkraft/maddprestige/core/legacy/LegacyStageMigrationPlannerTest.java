package net.maddkraft.maddprestige.core.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.core.config.BackupMetadata;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.core.rank.ReconciliationPolicy;
import net.maddkraft.maddprestige.core.stage.StageConfiguration;
import net.maddkraft.maddprestige.core.stage.StageDefinition;
import net.maddkraft.maddprestige.core.stage.StageProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LegacyStageMigrationPlannerTest {
    @Test
    @DisplayName("[A35][A36] Read-only detection plus explicit revisioned mapping produces a deterministic dry run")
    void detectsAndMapsSyntheticLegacyState() {
        String source = """
                ranks:
                  LEGACY_ALPHA: {display-name: Alpha}
                patrons:
                  parallel_identity: true
                """;
        var detection = new LegacyStageDetector().detect(source, List.of("LEGACY_ALPHA"));
        assertTrue(detection.legacyStageValues().contains("LEGACY_ALPHA"));
        assertFalse(detection.findings().findings().isEmpty());

        UUID player = UUID.randomUUID();
        LegacyStageMappingManifest manifest = new LegacyStageMappingManifest("manifest_revision_1",
                List.of(new LegacyStageMappingEntry("LEGACY_ALPHA", new StageId("first"))));
        LegacyStageMigrationPlan plan = new LegacyStageMigrationPlanner().plan(
                List.of(new LegacyPlayerStageRecord(player, "LEGACY_ALPHA")), manifest,
                target(), true, Optional.empty());
        assertTrue(plan.canProceed());
        assertTrue(plan.dryRun());
        assertEquals(new StageId("first"), plan.plannedPlayerMappings().get(player));
    }

    @Test
    @DisplayName("Exact nested V1 config shape is detected without treating patrons as progression")
    void detectsNestedV1ConfigurationShape() {
        String source = """
                integrations:
                  luckperms:
                    progression-groups:
                      CURIOUS: curious
                      ODD: odd
                progression:
                  ranks:
                    CURIOUS: {display-name: Curious}
                    ODD: {display-name: Odd}
                rewards:
                  patron-permissions:
                    homes: {}
                patron:
                  tiers:
                    OLD_SUPPORTER: {luckperms-group: old_supporter}
                maddhatter:
                  minimum-prestige: 10
                """;

        var detection = new LegacyStageDetector().detect(source, List.of("CURIOUS"));

        assertEquals(java.util.Set.of("CURIOUS", "ODD"), detection.legacyStageValues());
        assertEquals(java.util.Set.of(
                        "legacy.stage.fixed_rank_config",
                        "legacy.stage.external_group_config",
                        "legacy.stage.parallel_identity",
                        "legacy.config.obsolete_competition",
                        "legacy.stage.stored_values"),
                detection.findings().findings().stream()
                        .map(finding -> finding.code()).collect(java.util.stream.Collectors.toSet()));
        assertEquals("legacy.progression.ranks", detection.findings().findings().stream()
                .filter(finding -> finding.code().equals("legacy.stage.fixed_rank_config"))
                .findFirst().orElseThrow().path());
    }

    @Test
    @DisplayName("[A36] Missing and ambiguous mappings stop instead of guessing")
    void missingAndAmbiguousMappingsFail() {
        UUID player = UUID.randomUUID();
        LegacyStageMigrationPlanner planner = new LegacyStageMigrationPlanner();
        var missing = planner.plan(List.of(new LegacyPlayerStageRecord(player, "UNKNOWN")),
                new LegacyStageMappingManifest("manifest_revision_1", List.of()), target(), true, Optional.empty());
        assertTrue(missing.report().hasErrors());
        assertTrue(codes(missing).contains("legacy.mapping.missing"));
        assertFalse(missing.canProceed());
        assertTrue(missing.plannedPlayerMappings().isEmpty());

        var ambiguous = planner.plan(List.of(new LegacyPlayerStageRecord(player, "SOURCE")),
                new LegacyStageMappingManifest("manifest_revision_2", List.of(
                        new LegacyStageMappingEntry("SOURCE", new StageId("first")),
                        new LegacyStageMappingEntry("SOURCE", new StageId("second")))),
                target(), true, Optional.empty());
        assertTrue(codes(ambiguous).contains("legacy.mapping.ambiguous"));
        assertFalse(ambiguous.canProceed());
        assertTrue(ambiguous.plannedPlayerMappings().isEmpty());

        var invalidTarget = planner.plan(List.of(new LegacyPlayerStageRecord(player, "SOURCE")),
                new LegacyStageMappingManifest("manifest_revision_3", List.of(
                        new LegacyStageMappingEntry("SOURCE", new StageId("missing")))),
                target(), true, Optional.empty());
        assertTrue(codes(invalidTarget).contains("legacy.mapping.target_invalid"));
        assertFalse(invalidTarget.canProceed());
        assertTrue(invalidTarget.plannedPlayerMappings().isEmpty());
    }

    @Test
    @DisplayName("[A35] Non-dry migration planning remains backup-gated and has no mutation executor")
    void mutationPlanningRequiresVerifiedBackup() {
        LegacyStageMappingManifest manifest = new LegacyStageMappingManifest("manifest_revision_3", List.of());
        var blocked = new LegacyStageMigrationPlanner().plan(List.of(), manifest, target(), false, Optional.empty());
        assertTrue(codes(blocked).contains("legacy.backup.required"));
        BackupMetadata verified = new BackupMetadata("backup", RevisionHasher.hashText("fixture"),
                Instant.parse("2026-08-15T00:00:00Z"), true);
        assertFalse(new LegacyStageMigrationPlanner().plan(
                List.of(), manifest, target(), false, Optional.of(verified)).report().hasErrors());
    }

    private static StageConfiguration target() {
        StageId first = new StageId("first");
        StageId second = new StageId("second");
        return new StageConfiguration(2, true, Map.of(
                first, new StageDefinition(first, true, "First", Map.of(), StageProjection.none()),
                second, new StageDefinition(second, true, "Second", Map.of(), StageProjection.none())),
                List.of(first, second), Optional.of(first), ReconciliationPolicy.WARN_ONLY);
    }

    private static List<String> codes(LegacyStageMigrationPlan plan) {
        return plan.report().findings().stream().map(finding -> finding.code()).toList();
    }
}
