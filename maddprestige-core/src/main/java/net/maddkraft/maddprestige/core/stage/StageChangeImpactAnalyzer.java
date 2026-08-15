package net.maddkraft.maddprestige.core.stage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.validation.ValidationFinding;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.api.validation.ValidationSeverity;
import net.maddkraft.maddprestige.core.config.DiffKind;
import net.maddkraft.maddprestige.core.config.SemanticDiff;
import net.maddkraft.maddprestige.core.config.SemanticDiffEntry;
import net.maddkraft.maddprestige.core.schema.ReloadBehavior;
import net.maddkraft.maddprestige.core.schema.RiskLevel;

public final class StageChangeImpactAnalyzer {
    public StageChangeImpact analyze(
            StageConfiguration oldConfiguration,
            StageConfiguration newConfiguration,
            Map<StageId, Long> playerReferences,
            Optional<StageRemapPlan> remapPlan) {
        Set<StageId> oldIds = oldConfiguration.stages().keySet();
        Set<StageId> newIds = newConfiguration.stages().keySet();
        Set<StageId> added = difference(newIds, oldIds);
        Set<StageId> removed = difference(oldIds, newIds);
        Set<StageId> enabled = new LinkedHashSet<>();
        Set<StageId> disabled = new LinkedHashSet<>();
        for (StageId id : intersection(oldIds, newIds)) {
            boolean wasEnabled = oldConfiguration.stages().get(id).enabled();
            boolean nowEnabled = newConfiguration.stages().get(id).enabled();
            if (!wasEnabled && nowEnabled) {
                enabled.add(id);
            } else if (wasEnabled && !nowEnabled) {
                disabled.add(id);
            }
        }

        ArrayList<StageProjectionChange> projectionChanges = new ArrayList<>();
        for (StageId id : union(oldIds, newIds)) {
            Optional<StageProjection> oldProjection = Optional.ofNullable(oldConfiguration.stages().get(id))
                    .map(StageDefinition::projection);
            Optional<StageProjection> newProjection = Optional.ofNullable(newConfiguration.stages().get(id))
                    .map(StageDefinition::projection);
            if (!oldProjection.equals(newProjection)) {
                projectionChanges.add(new StageProjectionChange(id, oldProjection, newProjection));
            }
        }

        Set<StageId> unsafeIds = union(removed, disabled);
        LinkedHashMap<StageId, Long> affected = new LinkedHashMap<>();
        unsafeIds.forEach(id -> {
            long count = playerReferences.getOrDefault(id, 0L);
            if (count > 0) {
                affected.put(id, count);
            }
        });
        ArrayList<ValidationFinding> findings = new ArrayList<>();
        boolean remapRequired = !affected.isEmpty();
        if (remapRequired) {
            boolean planComplete = coversAffected(remapPlan, affected.keySet(), newConfiguration, findings);
            if (!planComplete) {
                findings.add(error("stage.change.remap_required", "progression.stages",
                        "Referenced stages would be removed or disabled: " + renderCounts(affected),
                        "Attach an explicit revisioned remap plan covering every referenced stage."));
            } else {
                findings.add(error("stage.change.remap_execution_required", "progression.stages",
                        "The remap plan is complete, but persisted player rows still reference stages being removed "
                                + "or disabled: " + renderCounts(affected),
                        "Execute and verify an atomic player-stage remap in a later authorized phase, then prepare "
                                + "the configuration again after the reference counts reach zero."));
            }
        }
        if (!oldConfiguration.order().equals(newConfiguration.order())) {
            findings.add(acknowledgement("stage.order.semantic_change", "progression.order",
                    "Stage order changes progression semantics from " + render(oldConfiguration.order())
                            + " to " + render(newConfiguration.order()) + ".",
                    "Review and explicitly acknowledge the reordered ladder before apply."));
        }
        if (!projectionChanges.isEmpty()) {
            findings.add(acknowledgement("stage.projection.mapping_change", "progression.stages",
                    "External projection mappings change for " + projectionChanges.size() + " stage(s).",
                    "Review provider/group effects and acknowledge before apply."));
        }

        ArrayList<SemanticDiffEntry> entries = new ArrayList<>();
        added.forEach(id -> entries.add(entry(id, DiffKind.ADDED, Optional.empty(), Optional.of(id.value()))));
        removed.forEach(id -> entries.add(entry(id, DiffKind.REMOVED, Optional.of(id.value()), Optional.empty())));
        if (!oldConfiguration.order().equals(newConfiguration.order())) {
            entries.add(new SemanticDiffEntry("progression.order", DiffKind.REORDERED,
                    Optional.of(render(oldConfiguration.order())), Optional.of(render(newConfiguration.order())),
                    RiskLevel.HIGH, ReloadBehavior.HOT_RELOAD));
        }
        projectionChanges.forEach(change -> entries.add(new SemanticDiffEntry(
                "progression.stages." + change.stageId().value() + ".projection", DiffKind.CHANGED,
                change.oldProjection().map(Object::toString), change.newProjection().map(Object::toString),
                RiskLevel.HIGH, ReloadBehavior.HOT_RELOAD)));
        return new StageChangeImpact(oldConfiguration.order(), newConfiguration.order(), added, removed,
                enabled, disabled, projectionChanges, affected, remapRequired, new SemanticDiff(entries),
                ValidationReport.of(findings));
    }

    private static boolean coversAffected(
            Optional<StageRemapPlan> plan,
            Set<StageId> affected,
            StageConfiguration newConfiguration,
            List<ValidationFinding> findings) {
        if (plan.isEmpty()) {
            return false;
        }
        boolean valid = true;
        for (StageId source : affected) {
            StageId target = plan.orElseThrow().mappings().get(source);
            StageDefinition targetDefinition = target == null ? null : newConfiguration.stages().get(target);
            if (targetDefinition == null || !targetDefinition.enabled() || !newConfiguration.order().contains(target)) {
                findings.add(error("stage.change.remap_invalid", "progression.stages." + source.value(),
                        "Remap target is missing, disabled, or unordered for referenced stage '" + source.value() + "'.",
                        "Map it explicitly to an enabled ordered stage in the candidate configuration."));
                valid = false;
            }
        }
        return valid;
    }

    private static SemanticDiffEntry entry(
            StageId id, DiffKind kind, Optional<String> oldValue, Optional<String> newValue) {
        return new SemanticDiffEntry("progression.stages." + id.value(), kind, oldValue, newValue,
                RiskLevel.HIGH, ReloadBehavior.HOT_RELOAD);
    }

    private static String renderCounts(Map<StageId, Long> counts) {
        return counts.entrySet().stream().map(entry -> entry.getKey().value() + "=" + entry.getValue())
                .sorted().reduce((left, right) -> left + ", " + right).orElse("none");
    }

    private static String render(List<StageId> order) {
        return order.stream().map(StageId::value).reduce((left, right) -> left + " -> " + right).orElse("(empty)");
    }

    private static Set<StageId> difference(Set<StageId> left, Set<StageId> right) {
        LinkedHashSet<StageId> result = new LinkedHashSet<>(left);
        result.removeAll(right);
        return result;
    }

    private static Set<StageId> intersection(Set<StageId> left, Set<StageId> right) {
        HashSet<StageId> result = new HashSet<>(left);
        result.retainAll(right);
        return result;
    }

    private static Set<StageId> union(Set<StageId> left, Set<StageId> right) {
        LinkedHashSet<StageId> result = new LinkedHashSet<>(left);
        result.addAll(right);
        return result;
    }

    private static ValidationFinding error(String code, String path, String explanation, String remediation) {
        return finding(code, ValidationSeverity.ERROR, path, explanation, remediation);
    }

    private static ValidationFinding acknowledgement(
            String code, String path, String explanation, String remediation) {
        return finding(code, ValidationSeverity.ACKNOWLEDGEMENT_REQUIRED, path, explanation, remediation);
    }

    private static ValidationFinding finding(
            String code, ValidationSeverity severity, String path, String explanation, String remediation) {
        return new ValidationFinding(code, severity, path, explanation,
                "The semantic impact is visible before activation and stored stage IDs are never reinterpreted by ordinal.",
                remediation);
    }
}
