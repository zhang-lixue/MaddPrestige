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
        StageConfiguration oldConfiguration = configuration(List.of("one", "two", "three"), "two_group");
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
    @DisplayName("[A69] Referenced removal remains blocked even when a complete remap plan exists")
    void requiresExecutedRemapForReferencedRemoval() {
        StageConfiguration oldConfiguration = configuration(List.of("one", "two", "three"), "two_group");
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
        assertTrue(covered.validation().hasErrors());
        assertTrue(codes(covered).contains("stage.change.remap_execution_required"));
        assertEquals(4L, covered.affectedPlayerReferences().get(new StageId("two")));
    }

    @Test
    @DisplayName("[A69] Referenced disablement also remains blocked until remap execution removes references")
    void requiresExecutedRemapForReferencedDisablement() {
        StageConfiguration oldConfiguration = configuration(List.of("one", "two", "three"), "two_group");
        StageConfiguration disabled = disabledConfiguration();
        StageRemapPlan remap = new StageRemapPlan("mapping_revision_2",
                Map.of(new StageId("two"), new StageId("three")));
        StageChangeImpact impact = new StageChangeImpactAnalyzer().analyze(oldConfiguration, disabled,
                Map.of(new StageId("two"), 2L), Optional.of(remap));
        assertTrue(impact.explicitRemapRequired());
        assertTrue(codes(impact).contains("stage.change.remap_execution_required"));
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
                        StageProjection.group(provider, "two_group")),
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
