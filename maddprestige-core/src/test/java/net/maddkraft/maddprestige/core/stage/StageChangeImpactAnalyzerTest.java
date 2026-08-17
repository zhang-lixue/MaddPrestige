package net.maddkraft.maddprestige.core.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.core.rank.ReconciliationPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StageChangeImpactAnalyzerTest {
    @Test
    @DisplayName("[A08] Reorder reports old/new semantics without rewriting stored stage IDs")
    void reportsReorderAndProjectionChanges() {
        StageConfiguration oldConfiguration = configuration(List.of("one", "two", "three"), "three_group");
        StageConfiguration newConfiguration = configuration(List.of("one", "three", "two"), "renamed_group");
        StageChangeImpact impact = new StageChangeImpactAnalyzer().analyze(
                oldConfiguration, newConfiguration, Map.of(new StageId("two"), 7L), Optional.empty());
        assertEquals(List.of("one", "two", "three"), values(impact.oldOrder()));
        assertEquals(List.of("one", "three", "two"), values(impact.newOrder()));
        assertEquals(1, impact.projectionChanges().size());
        assertTrue(impact.validation().findings().stream()
                .anyMatch(finding -> finding.code().equals("stage.order.semantic_change")));
        assertFalse(impact.explicitRemapRequired());
    }

    @Test
    @DisplayName("[A69] Complete projection-equivalent remap authorizes referenced removal with acknowledgement")
    void requiresExecutedRemapForReferencedRemoval() {
        StageConfiguration oldConfiguration = configuration(List.of("one", "two", "three"), "three_group");
        StageConfiguration removed = configuration(List.of("one", "three"), "unused");
        StageChangeImpact blocked = new StageChangeImpactAnalyzer().analyze(oldConfiguration, removed,
                Map.of(new StageId("two"), 4L), Optional.empty());
        assertTrue(blocked.explicitRemapRequired());
        assertTrue(blocked.validation().hasErrors());

        StageRemapPlan remap = new StageRemapPlan("mapping_revision_1",
                Map.of(new StageId("two"), new StageId("three")));
        StageChangeImpact covered = new StageChangeImpactAnalyzer().analyze(oldConfiguration, removed,
                Map.of(new StageId("two"), 4L), Optional.of(remap));
        assertTrue(covered.explicitRemapRequired());
        assertFalse(covered.validation().hasErrors());
        assertTrue(codes(covered).contains("stage.change.remap_migration"));
        assertEquals(4L, covered.affectedPlayerReferences().get(new StageId("two")));
    }

    @Test
    @DisplayName("[A69] Referenced disablement uses the same executable remap acknowledgement")
    void requiresExecutedRemapForReferencedDisablement() {
        StageConfiguration oldConfiguration = configuration(List.of("one", "two", "three"), "three_group");
        StageConfiguration disabled = disabledConfiguration();
        StageRemapPlan remap = new StageRemapPlan("mapping_revision_2",
                Map.of(new StageId("two"), new StageId("three")));
        StageChangeImpact impact = new StageChangeImpactAnalyzer().analyze(oldConfiguration, disabled,
                Map.of(new StageId("two"), 2L), Optional.of(remap));
        assertTrue(impact.explicitRemapRequired());
        assertFalse(impact.validation().hasErrors());
        assertTrue(codes(impact).contains("stage.change.remap_migration"));
        assertEquals(Set.of(new StageId("two")), impact.disabledStages());
    }

    @Test
    @DisplayName("[A69] Removal with zero stored references needs no remap execution blocker")
    void zeroReferenceRemovalMayProceed() {
        StageConfiguration oldConfiguration = configuration(List.of("one", "two", "three"), "two_group");
        StageConfiguration removed = configuration(List.of("one", "three"), "unused");
        StageChangeImpact impact = new StageChangeImpactAnalyzer().analyze(
                oldConfiguration, removed, Map.of(), Optional.empty());
        assertFalse(impact.explicitRemapRequired());
        assertFalse(impact.validation().hasErrors());
    }

    @Test
    @DisplayName("[A41][A69] Candidate-only remap target is rejected before destructive migration")
    void rejectsTargetIntroducedOnlyByCandidate() {
        StageConfiguration prior = exactConfiguration(
                Map.of(new StageId("one"), definition("one", true, "none"),
                        new StageId("source"), definition("source", true, "shared")),
                List.of("one", "source"));
        StageConfiguration candidate = exactConfiguration(
                Map.of(new StageId("one"), definition("one", true, "none"),
                        new StageId("target"), definition("target", true, "shared")),
                List.of("one", "target"));

        StageChangeImpact impact = analyzeRemap(prior, candidate, Map.of("source", "target"));

        assertTrue(codes(impact).contains("stage.change.remap_fallback_invalid"));
        assertTrue(impact.validation().hasErrors());
    }

    @Test
    @DisplayName("[A41][A69] Disabled fallback target is rejected even when candidate enables it")
    void rejectsTargetDisabledByPriorAuthority() {
        StageConfiguration prior = exactConfiguration(Map.of(
                new StageId("source"), definition("source", true, "shared"),
                new StageId("target"), definition("target", false, "shared")), List.of("source", "target"));
        StageConfiguration candidate = exactConfiguration(Map.of(
                new StageId("target"), definition("target", true, "shared")), List.of("target"));

        StageChangeImpact impact = analyzeRemap(prior, candidate, Map.of("source", "target"));

        assertTrue(codes(impact).contains("stage.change.remap_fallback_invalid"));
    }

    @Test
    @DisplayName("[A41][A69] Unordered fallback target is rejected even when candidate orders it")
    void rejectsTargetUnorderedByPriorAuthority() {
        StageConfiguration prior = exactConfiguration(Map.of(
                new StageId("source"), definition("source", true, "shared"),
                new StageId("target"), definition("target", true, "shared")), List.of("source"));
        StageConfiguration candidate = exactConfiguration(Map.of(
                new StageId("target"), definition("target", true, "shared")), List.of("target"));

        StageChangeImpact impact = analyzeRemap(prior, candidate, Map.of("source", "target"));

        assertTrue(codes(impact).contains("stage.change.remap_fallback_invalid"));
    }

    @Test
    @DisplayName("[A41][A69] Projection mismatch under prior or candidate authority is rejected")
    void rejectsProjectionMismatchAcrossEitherAuthority() {
        StageConfiguration priorMismatch = exactConfiguration(Map.of(
                new StageId("source"), definition("source", true, "source_group"),
                new StageId("target"), definition("target", true, "other_group")),
                List.of("source", "target"));
        StageConfiguration candidateCompatible = exactConfiguration(Map.of(
                new StageId("target"), definition("target", true, "source_group")), List.of("target"));
        StageConfiguration priorCompatible = exactConfiguration(Map.of(
                new StageId("source"), definition("source", true, "source_group"),
                new StageId("target"), definition("target", true, "source_group")),
                List.of("source", "target"));
        StageConfiguration candidateChanged = exactConfiguration(Map.of(
                new StageId("target"), definition("target", true, "changed_group")), List.of("target"));

        assertTrue(codes(analyzeRemap(priorMismatch, candidateCompatible, Map.of("source", "target")))
                .contains("stage.change.remap_projection_incompatible"));
        assertTrue(codes(analyzeRemap(priorCompatible, candidateChanged, Map.of("source", "target")))
                .contains("stage.change.remap_projection_incompatible"));
    }

    @Test
    @DisplayName("[A41][A69] One invalid target blocks an otherwise valid multi-source remap")
    void oneInvalidTargetBlocksMixedMultiSourceRemap() {
        StageConfiguration prior = exactConfiguration(Map.of(
                new StageId("b"), definition("b", true, "first_group"),
                new StageId("c"), definition("c", true, "first_group"),
                new StageId("f"), definition("f", true, "second_group"),
                new StageId("e"), definition("e", true, "second_group")), List.of("b", "c", "f", "e"));
        StageConfiguration candidate = exactConfiguration(Map.of(
                new StageId("c"), definition("c", true, "first_group"),
                new StageId("e"), definition("e", true, "changed_group")), List.of("c", "e"));

        StageChangeImpact impact = new StageChangeImpactAnalyzer().analyze(prior, candidate,
                Map.of(new StageId("b"), 2L, new StageId("f"), 3L), Optional.of(new StageRemapPlan("mixed",
                        Map.of(new StageId("b"), new StageId("c"), new StageId("f"), new StageId("e")))));

        assertTrue(impact.validation().hasErrors());
        assertTrue(codes(impact).contains("stage.change.remap_projection_incompatible"));
    }

    @Test
    @DisplayName("[A41][A69] Multi-source remap succeeds when every target is valid under both authorities")
    void acceptsEveryTargetValidUnderPriorAndCandidateAuthority() {
        StageConfiguration prior = exactConfiguration(Map.of(
                new StageId("b"), definition("b", true, "first_group"),
                new StageId("c"), definition("c", true, "first_group"),
                new StageId("f"), definition("f", true, "second_group"),
                new StageId("e"), definition("e", true, "second_group")), List.of("b", "c", "f", "e"));
        StageConfiguration candidate = exactConfiguration(Map.of(
                new StageId("c"), definition("c", true, "first_group"),
                new StageId("e"), definition("e", true, "second_group")), List.of("c", "e"));

        StageChangeImpact impact = new StageChangeImpactAnalyzer().analyze(prior, candidate,
                Map.of(new StageId("b"), 2L, new StageId("f"), 3L), Optional.of(new StageRemapPlan("safe",
                        Map.of(new StageId("b"), new StageId("c"), new StageId("f"), new StageId("e")))));

        assertFalse(impact.validation().hasErrors());
        assertEquals(Set.of(new StageId("b"), new StageId("f")), impact.affectedPlayerReferences().keySet());
    }

    private static StageChangeImpact analyzeRemap(
            StageConfiguration prior,
            StageConfiguration candidate,
            Map<String, String> mapping) {
        Map<StageId, StageId> mappings = mapping.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                entry -> new StageId(entry.getKey()), entry -> new StageId(entry.getValue())));
        Map<StageId, Long> references = mappings.keySet().stream().collect(java.util.stream.Collectors.toMap(
                stage -> stage, ignored -> 1L));
        return new StageChangeImpactAnalyzer().analyze(prior, candidate, references,
                Optional.of(new StageRemapPlan("fallback_matrix", mappings)));
    }

    private static StageConfiguration exactConfiguration(Map<StageId, StageDefinition> stages, List<String> order) {
        return new StageConfiguration(2, true, stages, order.stream().map(StageId::new).toList(), Optional.empty(),
                ReconciliationPolicy.WARN_ONLY);
    }

    private static StageDefinition definition(String id, boolean enabled, String group) {
        StageProjection projection = "none".equals(group) ? StageProjection.none()
                : StageProjection.group(new ProviderId("rank_provider"), group);
        StageId stageId = new StageId(id);
        return new StageDefinition(stageId, enabled, id, Map.of(), projection);
    }

    private static StageConfiguration configuration(List<String> order, String twoGroup) {
        LinkedHashMap<StageId, StageDefinition> stages = new LinkedHashMap<>();
        for (String id : order) {
            StageProjection projection = id.equals("one") ? StageProjection.none()
                    : StageProjection.group(new ProviderId("rank_provider"),
                            id.equals("two") ? twoGroup : id + "_group");
            StageId stageId = new StageId(id);
            stages.put(stageId, new StageDefinition(stageId, true, id, Map.of(), projection));
        }
        return new StageConfiguration(2, true, stages, order.stream().map(StageId::new).toList(),
                Optional.of(new StageId("one")), ReconciliationPolicy.WARN_ONLY);
    }

    private static StageConfiguration disabledConfiguration() {
        StageId one = new StageId("one");
        StageId two = new StageId("two");
        StageId three = new StageId("three");
        ProviderId provider = new ProviderId("rank_provider");
        return new StageConfiguration(2, true, Map.of(
                one, new StageDefinition(one, true, "one", Map.of(), StageProjection.none()),
                two, new StageDefinition(two, false, "two", Map.of(),
                        StageProjection.group(provider, "three_group")),
                three, new StageDefinition(three, true, "three", Map.of(),
                        StageProjection.group(provider, "three_group"))),
                List.of(one, three), Optional.of(one), ReconciliationPolicy.WARN_ONLY);
    }

    private static List<String> codes(StageChangeImpact impact) {
        return impact.validation().findings().stream().map(finding -> finding.code()).toList();
    }

    private static List<String> values(List<StageId> order) {
        return order.stream().map(StageId::value).toList();
    }
}
