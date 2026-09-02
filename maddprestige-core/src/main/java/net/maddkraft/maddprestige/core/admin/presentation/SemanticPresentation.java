package net.maddkraft.maddprestige.core.admin.presentation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.maddkraft.maddprestige.api.explanation.ExplanationNode;
import net.maddkraft.maddprestige.api.validation.ValidationFinding;
import net.maddkraft.maddprestige.core.admin.AdministrationException;
import net.maddkraft.maddprestige.core.admin.AdministrationSemanticVariant;
import net.maddkraft.maddprestige.core.admin.OperationPreview;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationExplanation;
import net.maddkraft.maddprestige.core.admin.diagnostic.DiagnosticFinding;
import net.maddkraft.maddprestige.core.admin.diagnostic.WhyReport;
import net.maddkraft.maddprestige.core.authorization.AuthorizationBlocker;

/**
 * Converts stable domain identities and typed facts into locale-neutral public messages. Stored English diagnostic
 * prose is deliberately not accepted here: the selected catalog owns every public explanation and remediation.
 */
public final class SemanticPresentation {
    private static final int MAX_NORMAL_REQUIREMENTS = 3;
    private static final int MAX_NORMAL_LINES = 8;
    private static final Map<String, Keys> ADMINISTRATION_KEYS = administrationMappings();
    private static final Map<AdministrationSemanticVariant, Keys> ADMINISTRATION_VARIANT_KEYS =
            administrationVariantMappings();

    private SemanticPresentation() {
    }

    public static List<MessageReference> doctorFinding(DiagnosticFinding finding) {
        Objects.requireNonNull(finding, "diagnostic finding");
        Keys keys = doctorKeys(doctorCategory(finding));
        return List.of(message(keys.summary(), "status", finding.severity(), "path", finding.path(),
                "code", finding.code(), "component", finding.component()),
                message(keys.remediation(), "status", finding.severity(), "path", finding.path(),
                        "code", finding.code(), "component", finding.component()));
    }

    public static MessageReference doctorFindingSummary(DiagnosticFinding finding) {
        Objects.requireNonNull(finding, "diagnostic finding");
        return message("command.doctor.finding", "status", finding.severity(), "path", finding.path(),
                "code", finding.code(), "component", finding.component());
    }

    public static List<MessageReference> validationFinding(ValidationFinding finding) {
        Objects.requireNonNull(finding, "validation finding");
        Keys keys = validationKeys(validationCategory(finding.code()));
        return List.of(message(keys.summary(), "status", finding.severity(), "path", finding.path(),
                "code", finding.code(), "reason", finding.explanation()),
                message(keys.remediation(), "status", finding.severity(),
                        "path", finding.path(), "code", finding.code(), "reason", finding.explanation()));
    }

    public static MessageReference validationFindingSummary(ValidationFinding finding) {
        Objects.requireNonNull(finding, "validation finding");
        if (finding.code().contains("scaling.")) {
            return message("command.validation.finding.scaling", "status", finding.severity(),
                    "path", finding.path(), "code", finding.code(), "reason", finding.explanation());
        }
        return message("command.validation.finding", "status", finding.severity(), "path", finding.path(),
                "code", finding.code());
    }

    public static List<MessageReference> why(WhyReport report) {
        return why(report, true);
    }

    public static List<MessageReference> why(WhyReport report, boolean details) {
        Objects.requireNonNull(report, "why report");
        if (!details) {
            return conciseWhy(report);
        }
        ArrayList<MessageReference> lines = new ArrayList<>();
        lines.add(message(report.executable() ? "command.why.allowed" : "command.why.blocked",
                "blockers", report.blockers().size()));
        if (!report.authorizationBlockers().isEmpty()) {
            lines.add(message("command.details.section.safety"));
            report.authorizationBlockers().forEach(blocker -> lines.add(authorizationBlocker(blocker)));
        }
        report.requirementExplanation().ifPresent(value -> {
            lines.add(message("command.details.section.requirements"));
            appendRequirement(value, lines);
        });
        report.configRevision().ifPresent(value -> {
            lines.add(message("command.details.section.provenance"));
            lines.add(message("command.why.revision", "revision", value.value()));
        });
        return List.copyOf(lines);
    }

    public static MessageReference authorizationBlocker(AuthorizationBlocker blocker) {
        Objects.requireNonNull(blocker, "authorization blocker");
        LinkedHashMap<String, String> arguments = new LinkedHashMap<>(blocker.facts());
        arguments.put("code", blocker.kind().catalogIdentity());
        return new MessageReference("command.why.blocker." + blocker.kind().catalogIdentity(), arguments);
    }

    public static List<MessageReference> preview(String summaryKey, OperationPreview preview) {
        return preview(summaryKey, preview, true);
    }

    public static List<MessageReference> preview(String summaryKey, OperationPreview preview, boolean details) {
        Objects.requireNonNull(summaryKey, "preview summary key");
        Objects.requireNonNull(preview, "operation preview");
        if (!details) {
            return concisePreview(preview);
        }
        ArrayList<MessageReference> lines = new ArrayList<>();
        lines.add(message(summaryKey, "kind", preview.kind(), "player", preview.playerId(),
                "status", preview.executable() ? "ELIGIBLE" : "BLOCKED",
                "blockers", preview.blockers().size(), "revision", preview.configRevision().value()));
        lines.add(message("command.preview.transition", "value", preview.stateChange()));
        preview.requirements().ifPresent(value -> {
            lines.add(message("command.details.section.requirements"));
            appendEffectiveRequirements(value, lines);
        });
        if (!preview.costs().isEmpty() || !preview.rewards().isEmpty() || !preview.milestones().isEmpty()) {
            lines.add(message("command.details.section.changes"));
        }
        preview.costs().forEach(value -> lines.add(message("command.preview.effective_cost", "value", value)));
        preview.rewards().forEach(value -> lines.add(message("command.preview.effective_reward", "value", value)));
        preview.milestones().forEach(value ->
                lines.add(message("command.preview.effective_milestone", "value", value)));
        if (!preview.authorizationBlockers().isEmpty()) {
            lines.add(message("command.details.section.safety"));
            preview.authorizationBlockers().forEach(blocker -> lines.add(authorizationBlocker(blocker)));
        }
        lines.add(message("command.details.section.provenance"));
        lines.addAll(preview.semanticDetails());
        preview.requirements().ifPresent(value -> appendRequirement(value, lines));
        preview.externalUncertainty().forEach(value ->
                lines.add(message("command.preview.effective_uncertainty", "value", value)));
        lines.add(message("command.preview.revision", "revision", preview.configRevision().value()));
        return List.copyOf(lines);
    }

    /** Three-line player status projection with no diagnostic or implementation vocabulary. */
    public static List<MessageReference> player(OperationPreview preview) {
        Objects.requireNonNull(preview, "Prestige preview");
        Map<String, String> transition = detail(preview, "command.preview.prestige_state_change")
                .map(MessageReference::arguments).orElse(Map.of());
        String current = transition.getOrDefault("current_prestige", prestigePart(preview.stateChange(), false));
        String target = transition.getOrDefault("target_prestige", prestigePart(preview.stateChange(), true));
        return List.of(
                message("command.player.concise.prestige", "current", current),
                message("command.player.concise.next", "target", target),
                message(preview.executable() ? "command.player.concise.ready" : "command.player.concise.not_ready"));
    }

    public static List<MessageReference> administration(AdministrationException exception) {
        Objects.requireNonNull(exception, "administration exception");
        Keys keys = exception.semanticVariant().map(variant -> Objects.requireNonNull(
                ADMINISTRATION_VARIANT_KEYS.get(variant), "administration semantic variant mapping"))
                .orElseGet(() -> ADMINISTRATION_KEYS.getOrDefault(exception.code(),
                        new Keys("command.error.administration.general.summary",
                                "command.error.administration.general.remediation")));
        ArrayList<MessageReference> lines = new ArrayList<>();
        lines.add(administrationMessage(keys.summary(), exception));
        exception.authorizationBlockers().forEach(blocker -> lines.add(authorizationBlocker(blocker)));
        lines.add(administrationMessage(keys.remediation(), exception));
        return List.copyOf(lines);
    }

    public static Set<String> knownAdministrationCodes() {
        return ADMINISTRATION_KEYS.keySet();
    }

    /** Exact public semantic identity selected for every known administration failure code. */
    public static Map<String, String> administrationSemanticIdentities() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        ADMINISTRATION_KEYS.forEach((code, keys) -> result.put(code, keys.identity()));
        return Map.copyOf(result);
    }

    /** Exact public semantic identity selected for every typed multi-source occurrence variant. */
    public static Map<AdministrationSemanticVariant, String> administrationSemanticVariantIdentities() {
        LinkedHashMap<AdministrationSemanticVariant, String> result = new LinkedHashMap<>();
        ADMINISTRATION_VARIANT_KEYS.forEach((variant, keys) -> result.put(variant, keys.identity()));
        return Map.copyOf(result);
    }

    public static MessageReference configurationDescription(ConfigurationExplanation explanation) {
        Objects.requireNonNull(explanation, "configuration explanation");
        return message(String.join(".", "command", "config", "description", explanation.descriptionIdentity()),
                "path", explanation.canonicalPath());
    }

    private static void appendRequirement(ExplanationNode node, List<MessageReference> lines) {
        lines.add(message("command.why.requirements", "status", node.status(),
                "id", node.facts().getOrDefault("requirement", node.code()),
                "metric", node.facts().getOrDefault("metric", "NONE"),
                "current", node.facts().getOrDefault("current", "UNAVAILABLE"),
                "target", node.facts().getOrDefault("target", "UNAVAILABLE")));
        if (node.facts().containsKey("provider")) {
            lines.add(message("command.preview.requirement_provenance",
                    "id", node.facts().getOrDefault("requirement", node.code()),
                    "provider", node.facts().getOrDefault("provider", "NONE"),
                    "scope", node.facts().getOrDefault("scope", "DEFAULT"),
                    "completion", node.facts().getOrDefault("completion", "DEFAULT"),
                    "operator", node.facts().getOrDefault("operator", "DEFAULT"),
                    "formula", node.facts().getOrDefault("effective-formula", "COMPILED_TARGET"),
                    "source", "COMPILED_EFFECTIVE_CONFIGURATION"));
        }
        node.children().forEach(child -> appendRequirement(child, lines));
    }

    private static List<MessageReference> conciseWhy(WhyReport report) {
        ArrayList<MessageReference> lines = new ArrayList<>();
        lines.add(message(report.executable() ? "command.concise.ready" : "command.concise.not_ready"));
        report.requirementExplanation().ifPresent(root -> appendConciseRequirementLeaves(root, lines,
                MAX_NORMAL_REQUIREMENTS));
        for (AuthorizationBlocker blocker : report.authorizationBlockers()) {
            if (lines.size() >= MAX_NORMAL_LINES || blocker.kind().catalogIdentity().contains("requirement")) {
                continue;
            }
            MessageReference summary = conciseBlocker(blocker);
            if (lines.stream().noneMatch(summary::equals)) {
                lines.add(summary);
            }
        }
        if (!report.executable() && lines.size() == 1) {
            lines.add(message("command.concise.blocker.unavailable"));
        }
        return List.copyOf(lines);
    }

    private static List<MessageReference> concisePreview(OperationPreview preview) {
        ArrayList<MessageReference> lines = new ArrayList<>();
        lines.add(message("command.concise.transition", "value", preview.stateChange()));
        preview.requirements().ifPresent(root -> {
            lines.add(message(root.status() == net.maddkraft.maddprestige.api.explanation.ExplanationStatus.SATISFIED
                    ? "command.concise.requirements_met" : "command.concise.requirements_not_met"));
            appendConciseRequirementLeaves(root, lines, 2);
        });
        List<MessageReference> costs = details(preview, "command.preview.cost");
        if (!costs.isEmpty()) {
            lines.add(message("command.concise.cost", "amount",
                    costs.stream().map(SemanticPresentation::gameplayAmount)
                            .collect(java.util.stream.Collectors.joining(" + "))));
        } else if (!preview.costs().isEmpty()) {
            lines.add(message("command.concise.cost_text", "value", String.join(" + ", preview.costs())));
        }
        List<MessageReference> rewards = details(preview, "command.preview.reward");
        if (!rewards.isEmpty()) {
            lines.add(message("command.concise.reward", "amount",
                    rewards.stream().map(SemanticPresentation::gameplayAmount)
                            .collect(java.util.stream.Collectors.joining(" + "))));
        } else if (!preview.rewards().isEmpty()) {
            lines.add(message("command.concise.reward_text", "value", String.join(" + ", preview.rewards())));
        }
        for (AuthorizationBlocker blocker : preview.authorizationBlockers()) {
            if (lines.size() >= MAX_NORMAL_LINES || blocker.kind().catalogIdentity().contains("requirement")) {
                continue;
            }
            MessageReference summary = conciseBlocker(blocker);
            if (lines.stream().noneMatch(summary::equals)) {
                lines.add(summary);
            }
        }
        return List.copyOf(lines.subList(0, Math.min(lines.size(), MAX_NORMAL_LINES)));
    }

    private static void appendConciseRequirementLeaves(
            ExplanationNode root,
            List<MessageReference> lines,
            int maximum) {
        ArrayList<ExplanationNode> leaves = new ArrayList<>();
        collectLeaves(root, leaves);
        leaves.stream().limit(maximum).forEach(leaf -> lines.add(message("command.concise.requirement",
                "label", gameplayLabel(leaf),
                "current", leaf.facts().getOrDefault("current", "Unavailable"),
                "target", leaf.facts().getOrDefault("target", "Unavailable"),
                "indicator", leaf.status()
                        == net.maddkraft.maddprestige.api.explanation.ExplanationStatus.SATISFIED ? "✓" : "✗")));
    }

    private static void collectLeaves(ExplanationNode node, List<ExplanationNode> leaves) {
        if (node.children().isEmpty()) {
            leaves.add(node);
            return;
        }
        node.children().forEach(child -> collectLeaves(child, leaves));
    }

    private static String gameplayLabel(ExplanationNode node) {
        String metric = node.facts().getOrDefault("metric", "progress").toLowerCase(java.util.Locale.ROOT);
        if (metric.equals("balance") || metric.contains("money")) {
            return "Money";
        }
        if (metric.equals("total_level") || metric.contains("mcmmo")) {
            return "mcMMO";
        }
        if (metric.contains("mobcoin")) {
            return "MobCoins";
        }
        String[] words = metric.replace('-', '_').split("_+");
        return java.util.Arrays.stream(words).filter(value -> !value.isBlank())
                .map(value -> Character.toUpperCase(value.charAt(0)) + value.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private static String gameplayAmount(MessageReference reference) {
        String amount = reference.arguments().getOrDefault("canonical",
                reference.arguments().getOrDefault("amount", "0"));
        String provider = reference.arguments().getOrDefault("provider", "").toLowerCase(java.util.Locale.ROOT);
        String type = reference.arguments().getOrDefault("type", "").toLowerCase(java.util.Locale.ROOT);
        if (provider.contains("vault") || type.contains("money") || type.contains("withdraw")
                || type.contains("deposit")) {
            return "$" + amount;
        }
        String display = reference.arguments().getOrDefault("value", "");
        return display.isBlank() ? amount : amount + " " + display;
    }

    private static java.util.Optional<MessageReference> detail(OperationPreview preview, String key) {
        return preview.semanticDetails().stream().filter(reference -> reference.key().equals(key)).findFirst();
    }

    private static List<MessageReference> details(OperationPreview preview, String key) {
        return preview.semanticDetails().stream().filter(reference -> reference.key().equals(key)).toList();
    }

    private static MessageReference conciseBlocker(AuthorizationBlocker blocker) {
        String code = blocker.kind().catalogIdentity();
        if (code.contains("cooldown")) {
            return message("command.concise.blocker.cooldown", "value",
                    blocker.facts().getOrDefault("cooldown_remaining", "active"));
        }
        if (code.contains("maximum")) {
            return message("command.concise.blocker.maximum");
        }
        if (code.contains("cost")) {
            return message("command.concise.blocker.cost");
        }
        if (code.contains("provider") || code.contains("boundary") || code.contains("projection")) {
            return message("command.concise.blocker.provider");
        }
        if (code.contains("state") || code.contains("revision") || code.contains("context")) {
            return message("command.concise.blocker.state");
        }
        return message("command.concise.blocker.unavailable");
    }

    private static String prestigePart(String value, boolean target) {
        int arrow = value.indexOf('→');
        if (arrow < 0) {
            return value;
        }
        String part = target ? value.substring(arrow + 1) : value.substring(0, arrow);
        return part.replace("Prestige", "").trim();
    }

    private static void appendEffectiveRequirements(ExplanationNode root, List<MessageReference> lines) {
        lines.add(message("command.preview.requirement_mode", "mode",
                root.facts().getOrDefault("mode", "SINGLE")));
        appendEffectiveRequirementLeaves(root, lines);
    }

    private static void appendEffectiveRequirementLeaves(ExplanationNode node, List<MessageReference> lines) {
        if (node.children().isEmpty()) {
            lines.add(message("command.preview.effective_requirement", "status", node.status(),
                    "id", node.facts().getOrDefault("requirement", node.code()),
                    "metric", node.facts().getOrDefault("metric", "NONE"),
                    "current", node.facts().getOrDefault("current", "UNAVAILABLE"),
                    "target", node.facts().getOrDefault("target", "UNAVAILABLE"),
                    "scaling", node.facts().getOrDefault("effective-formula", "COMPILED_TARGET")));
            return;
        }
        node.children().forEach(child -> appendEffectiveRequirementLeaves(child, lines));
    }

    private static String doctorCategory(DiagnosticFinding finding) {
        String code = finding.code();
        if (code.equals("config.active") || code.equals("config.inactive")) {
            return "active_configuration";
        }
        if (code.startsWith("provider.")) {
            return "provider_health";
        }
        if (code.startsWith("database.")) {
            return "database";
        }
        if (code.startsWith("rank.")) {
            return "rank_targets";
        }
        if (code.startsWith("config.history.")) {
            return "configuration_history";
        }
        if (code.startsWith("config.schema_")) {
            return "schema";
        }
        if (code.startsWith("requirement.metric") || code.startsWith("cost.provider")
                || code.startsWith("reward.provider")) {
            return "provider_reference";
        }
        if (code.startsWith("operation.pending")) {
            return "operation_pending";
        }
        if (code.contains("reconciliation") || code.contains("transition")) {
            return "reconciliation";
        }
        if (code.startsWith("player.stage")) {
            return "player_state";
        }
        if (code.contains("integrity") || code.contains("immutable_id")) {
            return "integrity";
        }
        if (code.startsWith("doctor.not_checked.")) {
            return "not_checked";
        }
        if (code.equals("doctor.probe.failed")) {
            return "probe_failure";
        }
        if (code.matches(".*\\.(healthy|warning|blocked|deferred)$")) {
            return "subsystem";
        }
        return "general";
    }

    private static Keys doctorKeys(String category) {
        return switch (category) {
            case "active_configuration" -> new Keys("command.doctor.finding.active_configuration.summary",
                    "command.doctor.finding.active_configuration.remediation");
            case "provider_health" -> new Keys("command.doctor.finding.provider_health.summary",
                    "command.doctor.finding.provider_health.remediation");
            case "database" -> new Keys("command.doctor.finding.database.summary",
                    "command.doctor.finding.database.remediation");
            case "rank_targets" -> new Keys("command.doctor.finding.rank_targets.summary",
                    "command.doctor.finding.rank_targets.remediation");
            case "configuration_history" -> new Keys("command.doctor.finding.configuration_history.summary",
                    "command.doctor.finding.configuration_history.remediation");
            case "schema" -> new Keys("command.doctor.finding.schema.summary",
                    "command.doctor.finding.schema.remediation");
            case "provider_reference" -> new Keys("command.doctor.finding.provider_reference.summary",
                    "command.doctor.finding.provider_reference.remediation");
            case "operation_pending" -> new Keys("command.doctor.finding.operation_pending.summary",
                    "command.doctor.finding.operation_pending.remediation");
            case "reconciliation" -> new Keys("command.doctor.finding.reconciliation.summary",
                    "command.doctor.finding.reconciliation.remediation");
            case "player_state" -> new Keys("command.doctor.finding.player_state.summary",
                    "command.doctor.finding.player_state.remediation");
            case "integrity" -> new Keys("command.doctor.finding.integrity.summary",
                    "command.doctor.finding.integrity.remediation");
            case "not_checked" -> new Keys("command.doctor.finding.not_checked.summary",
                    "command.doctor.finding.not_checked.remediation");
            case "probe_failure" -> new Keys("command.doctor.finding.probe_failure.summary",
                    "command.doctor.finding.probe_failure.remediation");
            case "subsystem" -> new Keys("command.doctor.finding.subsystem.summary",
                    "command.doctor.finding.subsystem.remediation");
            default -> new Keys("command.doctor.finding.general.summary",
                    "command.doctor.finding.general.remediation");
        };
    }

    private static String validationCategory(String code) {
        if (code.contains("scaling.")) {
            return "scaling";
        }
        if (code.contains("provider") || code.startsWith("phase3.provider")
                || code.startsWith("phase4.provider") || code.startsWith("phase5.")) {
            return "provider";
        }
        if (code.startsWith("stage.") || code.contains("rank_projection")) {
            return "stage";
        }
        if (code.startsWith("requirement") || code.contains("requirement")) {
            return "requirement";
        }
        if (code.startsWith("cost") || code.contains(".cost")) {
            return "cost";
        }
        if (code.startsWith("reward") || code.contains(".reward")) {
            return "reward";
        }
        if (code.startsWith("prestige")) {
            return "prestige";
        }
        if (code.startsWith("legacy") || code.contains("migration") || code.contains("remap")) {
            return "migration";
        }
        if (code.startsWith("integration") || code.startsWith("phase5")) {
            return "integration";
        }
        if (code.startsWith("currency")) {
            return "currency";
        }
        if (code.startsWith("command")) {
            return "command_action";
        }
        if (code.startsWith("season") || code.startsWith("entitlement") || code.startsWith("milestone")) {
            return "lifecycle";
        }
        return "configuration";
    }

    private static Keys validationKeys(String category) {
        return switch (category) {
            case "scaling" -> new Keys("command.validation.finding.scaling.summary",
                    "command.validation.finding.scaling.remediation");
            case "provider" -> new Keys("command.validation.finding.provider.summary",
                    "command.validation.finding.provider.remediation");
            case "stage" -> new Keys("command.validation.finding.stage.summary",
                    "command.validation.finding.stage.remediation");
            case "requirement" -> new Keys("command.validation.finding.requirement.summary",
                    "command.validation.finding.requirement.remediation");
            case "cost" -> new Keys("command.validation.finding.cost.summary",
                    "command.validation.finding.cost.remediation");
            case "reward" -> new Keys("command.validation.finding.reward.summary",
                    "command.validation.finding.reward.remediation");
            case "prestige" -> new Keys("command.validation.finding.prestige.summary",
                    "command.validation.finding.prestige.remediation");
            case "migration" -> new Keys("command.validation.finding.migration.summary",
                    "command.validation.finding.migration.remediation");
            case "integration" -> new Keys("command.validation.finding.integration.summary",
                    "command.validation.finding.integration.remediation");
            case "currency" -> new Keys("command.validation.finding.currency.summary",
                    "command.validation.finding.currency.remediation");
            case "command_action" -> new Keys("command.validation.finding.command_action.summary",
                    "command.validation.finding.command_action.remediation");
            case "lifecycle" -> new Keys("command.validation.finding.lifecycle.summary",
                    "command.validation.finding.lifecycle.remediation");
            default -> new Keys("command.validation.finding.configuration.summary",
                    "command.validation.finding.configuration.remediation");
        };
    }

    private static Map<String, Keys> administrationMappings() {
        LinkedHashMap<String, Keys> result = new LinkedHashMap<>();
        register(result, "command_player_required", "command.player_required");
        register(result, "config_acknowledgement_actor_mismatch", "config.acknowledgement.actor_mismatch");
        register(result, "config_acknowledgement_already_used", "config.acknowledgement.already_used");
        register(result, "config_acknowledgement_expired", "config.acknowledgement.expired");
        register(result, "config_acknowledgement_findings_changed", "config.acknowledgement.findings_changed");
        register(result, "config_acknowledgement_not_required", "config.acknowledgement.not_required");
        register(result, "config_acknowledgement_server_authority_required",
                "config.acknowledgement.server_authority_required");
        register(result, "config_acknowledgement_stale", "config.acknowledgement.stale");
        register(result, "config_acknowledgement_unknown", "config.acknowledgement.unknown");
        register(result, "config_active_absent", "config.active.absent");
        register(result, "config_add_rejected", "config.add.rejected");
        register(result, "config_apply_failed", "config.apply.failed");
        register(result, "config_apply_kind_mismatch", "config.apply.kind_mismatch");
        register(result, "config_document_missing", "config.document.missing");
        register(result, "config_draft_apply_in_progress", "config.draft.apply_in_progress");
        register(result, "config_draft_cancelled", "config.draft.cancelled");
        register(result, "config_draft_changed_during_apply", "config.draft.changed_during_apply");
        register(result, "config_draft_changed_during_prepare", "config.draft.changed_during_prepare");
        register(result, "config_draft_concurrent_change", "config.draft.concurrent_change");
        register(result, "config_draft_concurrent_edit", "config.draft.concurrent_edit");
        register(result, "config_draft_expired", "config.draft.expired");
        register(result, "config_draft_owner_mismatch", "config.draft.owner_mismatch");
        register(result, "config_draft_unknown", "config.draft.unknown");
        register(result, "config_edit_rejected", "config.edit.rejected");
        register(result, "config_history_finalize_failed", "config.history.finalize_failed");
        register(result, "config_list_rejected", "config.list.rejected");
        register(result, "config_path_not_editable", "config.path.not_editable");
        register(result, "config_path_not_listable", "config.path.not_listable");
        register(result, "config_path_type_mismatch", "config.path.type_mismatch");
        register(result, "config_path_unknown", "config.path.unknown");
        register(result, "config_preview_candidate_missing", "config.preview.candidate_missing");
        register(result, "config_preview_required", "config.preview.required");
        register(result, "config_preview_stale", "config.preview.stale");
        register(result, "config_remove_rejected", "config.remove.rejected");
        register(result, "config_revision_stale", "config.revision.stale");
        register(result, "config_rollback_not_applied", "config.rollback.not_applied");
        register(result, "config_rollback_not_prepared", "config.rollback.not_prepared");
        register(result, "config_rollback_unknown", "config.rollback.unknown");
        register(result, "config_snapshot_activate_failed", "config.snapshot.activate_failed");
        register(result, "config_snapshot_prepare_failed", "config.snapshot.prepare_failed");
        register(result, "config_structured_add_rejected", "config.structured.add_rejected");
        register(result, "config_structured_edit_rejected", "config.structured.edit_rejected");
        register(result, "config_structured_index_invalid", "config.structured.index_invalid");
        register(result, "config_structured_key_invalid", "config.structured.key_invalid");
        register(result, "config_structured_key_required", "config.structured.key_required");
        register(result, "config_structured_key_unexpected", "config.structured.key_unexpected");
        register(result, "config_structured_remove_rejected", "config.structured.remove_rejected");
        register(result, "config_validation_blocked", "config.validation.blocked");
        register(result, "config_value_not_allowed", "config.value.not_allowed");
        register(result, "confirmation_actor_mismatch", "confirmation.actor_mismatch");
        register(result, "confirmation_already_used", "confirmation.already_used");
        register(result, "confirmation_config_stale", "confirmation.config_stale");
        register(result, "confirmation_ambiguous", "confirmation.ambiguous");
        register(result, "confirmation_expired", "confirmation.expired");
        register(result, "confirmation_none_pending", "confirmation.none_pending");
        register(result, "confirmation_revalidation_failed", "confirmation.revalidation_failed");
        register(result, "confirmation_session_ended", "confirmation.session_ended");
        register(result, "confirmation_unknown", "confirmation.unknown");
        register(result, "gui_action_forged", "gui.action.forged");
        register(result, "gui_action_player_invalid", "gui.action.player_invalid");
        register(result, "gui_action_replayed", "gui.action.replayed");
        register(result, "gui_action_stale", "gui.action.stale");
        register(result, "gui_mutation_context_missing", "gui.mutation.context_missing");
        register(result, "gui_mutation_kind_invalid", "gui.mutation.kind_invalid");
        register(result, "gui_session_actor_mismatch", "gui.session.actor_mismatch");
        register(result, "gui_session_expired", "gui.session.expired");
        register(result, "gui_player_self_required", "gui.player.self_required");
        register(result, "gui_player_target_missing", "gui.player.target_missing");
        register(result, "gui_target_missing", "gui.target.missing");
        register(result, "operation_preview_blocked", "operation.preview.blocked");
        register(result, "permission_denied", "permission.denied");
        register(result, "rankup_compatibility_only", "rankup.compatibility_only");
        register(result, "setup_acknowledgement_unknown", "setup.acknowledgement.unknown");
        register(result, "setup_already_active", "setup.already_active");
        register(result, "setup_baseline_unknown", "setup.baseline.unknown");
        register(result, "setup_draft_invalid", "setup.draft.invalid");
        register(result, "setup_group_missing", "setup.group.missing");
        register(result, "setup_incomplete", "setup.incomplete");
        register(result, "setup_integration_unconfigurable", "setup.integration.unconfigurable");
        register(result, "setup_prestige_stage_unknown", "setup.prestige.stage_unknown");
        register(result, "setup_preview_required", "setup.preview.required");
        register(result, "setup_provider_required", "setup.provider.required");
        register(result, "setup_requirement_baseline", "setup.requirement.baseline");
        register(result, "setup_requirement_completion_invalid", "setup.requirement.completion.invalid");
        register(result, "setup_requirement_duplicate", "setup.requirement.duplicate");
        register(result, "setup_requirement_metric_unknown", "setup.requirement.metric_unknown");
        register(result, "setup_requirement_operator_invalid", "setup.requirement.operator.invalid");
        register(result, "setup_requirement_scope_invalid", "setup.requirement.scope.invalid");
        register(result, "setup_requirement_target_invalid", "setup.requirement.target.invalid");
        register(result, "setup_session_owner_mismatch", "setup.session.owner_mismatch");
        register(result, "setup_session_unknown", "setup.session.unknown");
        register(result, "setup_stage_duplicate", "setup.stage.duplicate");
        register(result, "setup_text_control_character", "setup.text.control_character");
        register(result, "stage_add_rejected", "stage.add.rejected");
        register(result, "stage_change_remap_candidate_invalid", "stage.change.remap_candidate_invalid");
        register(result, "stage_change_remap_invalid", "stage.change.remap_invalid");
        register(result, "stage_change_remap_missing", "stage.change.remap_missing");
        register(result, "stage_change_remap_required", "stage.change.remap_required");
        register(result, "stage_change_remap_snapshot_stale", "stage.change.remap_snapshot_stale");
        register(result, "stage_change_remap_source_missing", "stage.change.remap_source_missing");
        register(result, "stage_change_remap_source_present", "stage.change.remap_source_present");
        register(result, "stage_change_remap_target_missing", "stage.change.remap_target_missing");
        register(result, "stage_change_transition_reconciliation_pending",
                "stage.change.transition_reconciliation_pending");
        register(result, "stage_change_transition_stale", "stage.change.transition_stale");
        register(result, "stage_compatibility_only", "stage.compatibility_only");
        register(result, "stage_remove_rejected", "stage.remove.rejected");
        return Map.copyOf(result);
    }

    private static Map<AdministrationSemanticVariant, Keys> administrationVariantMappings() {
        LinkedHashMap<AdministrationSemanticVariant, Keys> result = new LinkedHashMap<>();
        result.put(AdministrationSemanticVariant.CONFIG_APPLY_PRIOR_STATE_UNCHANGED,
                administrationKeys("config_apply_failed_prior_state_unchanged"));
        result.put(AdministrationSemanticVariant.CONFIG_APPLY_PRIOR_STATE_RESTORED,
                administrationKeys("config_apply_failed_prior_state_restored"));
        result.put(AdministrationSemanticVariant.CONFIG_APPLY_RECONCILIATION_REQUIRED,
                administrationKeys("config_apply_failed_reconciliation_required"));
        result.put(AdministrationSemanticVariant.CONFIG_VALIDATION_ACKNOWLEDGEMENT_PREPARATION,
                administrationKeys("config_validation_blocked_acknowledgement_preparation"));
        result.put(AdministrationSemanticVariant.CONFIG_VALIDATION_APPLY,
                administrationKeys("config_validation_blocked_apply"));
        return Map.copyOf(result);
    }

    private static Keys administrationKeys(String identity) {
        return new Keys(identity, "command.error.administration." + identity + ".summary",
                "command.error.administration." + identity + ".remediation");
    }

    private static void register(Map<String, Keys> target, String identity, String... codes) {
        Keys keys = administrationKeys(identity);
        for (String code : codes) {
            if (target.put(code, keys) != null) {
                throw new IllegalStateException("Duplicate administration code mapping: " + code);
            }
        }
    }

    private static MessageReference administrationMessage(String key, AdministrationException exception) {
        LinkedHashMap<String, String> arguments = new LinkedHashMap<>(exception.facts());
        arguments.put("code", exception.code());
        exception.semanticVariant().ifPresent(variant -> arguments.put("variant", variant.name()));
        return new MessageReference(key, arguments);
    }

    private static MessageReference message(String key, Object... arguments) {
        return MessageReference.of(key, arguments);
    }

    private record Keys(String identity, String summary, String remediation) {
        private Keys(String summary, String remediation) {
            this("", summary, remediation);
        }
    }
}
