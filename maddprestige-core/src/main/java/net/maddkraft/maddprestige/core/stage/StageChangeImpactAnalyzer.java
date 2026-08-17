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
        if (!remapRequired && remapPlan.isPresent() && !remapPlan.orElseThrow().mappings().isEmpty()) {
            findings.add(error("stage.change.remap_extra_source", "progression.stages",
                    "The remap selects stages that have no affected persisted references: "
                            + render(remapPlan.orElseThrow().mappings().keySet()),
                    "Remove the remap or select it only after preview reports referenced missing stages."));
        }
        if (remapRequired) {
            boolean planComplete = coversAffected(remapPlan, affected.keySet(), oldConfiguration,
                    newConfiguration, findings);
            if (!planComplete) {
                findings.add(error("stage.change.remap_required", "progression.stages",
                        "Referenced stages would be removed or disabled: " + renderCounts(affected),
                        "Attach an explicit revisioned remap plan covering every referenced stage."));
            } else {
                findings.add(acknowledgement("stage.change.remap_migration", "progression.stages",
                        "Applying this revision migrates persisted player stage references before deleting or "
                                + "disabling stages: " + renderCounts(affected),
                        "Review the exact source-to-replacement plan and affected player count, then confirm it."));
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
            StageConfiguration oldConfiguration,
            StageConfiguration newConfiguration,
            List<ValidationFinding> findings) {
        if (plan.isEmpty()) {
            return false;
        }
        boolean valid = true;
        Set<StageId> extraSources = new LinkedHashSet<>(plan.orElseThrow().mappings().keySet());
        extraSources.removeAll(affected);
        if (!extraSources.isEmpty()) {
            findings.add(error("stage.change.remap_extra_source", "progression.stages",
                    "The remap includes stages that have no affected persisted references: " + render(extraSources),
                    "Remove unrelated mappings so the migration authority covers only the previewed references."));
            valid = false;
        }
        for (StageId source : affected) {
            StageId target = plan.orElseThrow().mappings().get(source);
            StageDefinition candidateTarget = target == null ? null : newConfiguration.stages().get(target);
            if (target == null || source.equals(target) || candidateTarget == null || !candidateTarget.enabled()
                    || !newConfiguration.order().contains(target)) {
                findings.add(error("stage.change.remap_invalid", "progression.stages." + source.value(),
                        "Remap target is missing, unchanged, disabled, or unordered for referenced stage '"
                                + source.value() + "'.",
                        "Map it to a different enabled ordered stage in the candidate configuration."));
                valid = false;
                continue;
            }
            StageDefinition sourceDefinition = oldConfiguration.stages().get(source);
            StageDefinition fallbackTarget = oldConfiguration.stages().get(target);
            if (fallbackTarget == null || !fallbackTarget.enabled() || !oldConfiguration.order().contains(target)) {
                findings.add(error("stage.change.remap_fallback_invalid",
                        "progression.stages." + source.value(),
                        "The replacement is not an enabled ordered stage in the currently authoritative "
                                + "fallback configuration.",
                        "Choose a replacement that is already valid under both the current and candidate "
                                + "configurations."));
                valid = false;
                continue;
            }
            if (sourceDefinition != null
                    && (!sourceDefinition.projection().equals(fallbackTarget.projection())
                            || !sourceDefinition.projection().equals(candidateTarget.projection())
                            || !fallbackTarget.projection().equals(candidateTarget.projection()))) {
                findings.add(error("stage.change.remap_projection_incompatible",
                        "progression.stages." + source.value(),
                        "The replacement does not preserve external projection semantics under both current "
                                + "fallback and candidate authority.",
                        "Choose a replacement whose current and candidate projection exactly matches the source, "
                                + "or use an explicitly recoverable provider-aware workflow."));
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

    private static String render(Set<StageId> stages) {
        return stages.stream().map(StageId::value).sorted()
                .reduce((left, right) -> left + ", " + right).orElse("none");
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
