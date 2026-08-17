package net.maddkraft.maddprestige.core.admin.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.validation.ValidationFinding;
import net.maddkraft.maddprestige.api.validation.ValidationSeverity;
import net.maddkraft.maddprestige.core.admin.AdministrationException;
import net.maddkraft.maddprestige.core.admin.PermissionSubject;
import net.maddkraft.maddprestige.core.admin.PhaseSixPermissions;
import net.maddkraft.maddprestige.core.config.ActiveConfiguration;
import net.maddkraft.maddprestige.core.config.ConfigCompiler;
import net.maddkraft.maddprestige.core.config.ConfigDraft;
import net.maddkraft.maddprestige.core.config.ConfigurationService;
import net.maddkraft.maddprestige.core.config.ContentHash;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.core.schema.SchemaNode;
import net.maddkraft.maddprestige.core.schema.SchemaRegistry;
import net.maddkraft.maddprestige.core.schema.SchemaValueType;
import net.maddkraft.maddprestige.core.stage.StageRemapPlan;
import net.maddkraft.maddprestige.core.stage.StageConfigurationCompiler;
import net.maddkraft.maddprestige.core.yaml.LosslessYamlDocument;
import net.maddkraft.maddprestige.core.yaml.YamlPath;

public final class ConfigurationAdministrationService {
    private static final int MAX_HISTORY_LIMIT = 1000;
    private static final Duration DRAFT_LIFETIME = Duration.ofHours(2);
    private static final Duration ACKNOWLEDGEMENT_LIFETIME = Duration.ofMinutes(5);
    private final ConfigurationService canonical;
    private final PhaseSixConfigurationWorkflow workflow;
    private final SchemaRegistry schema;
    private final ConfigurationHistoryStore history;
    private final ConfigurationSnapshotStore snapshots;
    private final Clock clock;
    private final ConfigurationPathResolver paths = new ConfigurationPathResolver();
    private final Map<UUID, DraftState> drafts = new ConcurrentHashMap<>();
    private final Map<UUID, ConfigurationAcknowledgement> acknowledgements = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> consumedAcknowledgements = new ConcurrentHashMap<>();

    public ConfigurationAdministrationService(
            ConfigurationService canonical,
            PhaseSixConfigurationWorkflow workflow,
            SchemaRegistry schema,
            ConfigurationHistoryStore history,
            ConfigurationSnapshotStore snapshots,
            Clock clock) {
        this.canonical = Objects.requireNonNull(canonical, "canonical configuration service");
        this.workflow = Objects.requireNonNull(workflow, "Phase 6 workflow");
        this.schema = Objects.requireNonNull(schema, "schema");
        this.history = Objects.requireNonNull(history, "history");
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public UUID beginDraft(PermissionSubject subject, String sourceSurface) {
        subject.require(PhaseSixPermissions.CONFIG_EDIT);
        ActiveConfiguration current = canonical.active().orElseThrow(() -> new AdministrationException(
                "config.active.absent", "There is no active configuration to edit.",
                "Use the setup wizard to create the first validated configuration."));
        return createDraft(subject, Optional.of(current.revisionId()), current.compiled().documents(),
                sourceSurface, Optional.empty(), ConfigurationApplyKind.NORMAL);
    }

    public UUID beginInitialDraft(
            PermissionSubject subject,
            Map<String, String> documents,
            String sourceSurface) {
        subject.require(PhaseSixPermissions.SETUP);
        if (canonical.active().isPresent()) {
            throw new AdministrationException("setup.already_active", "An active configuration already exists.",
                    "Create a normal draft instead of replacing the active revision through setup.");
        }
        return createDraft(subject, Optional.empty(), documents, sourceSurface, Optional.empty(),
                ConfigurationApplyKind.SETUP);
    }

    public UUID beginRollback(
            PermissionSubject subject,
            ConfigRevisionId targetRevision,
            String sourceSurface) {
        subject.require(PhaseSixPermissions.CONFIG_ROLLBACK);
        ActiveConfiguration current = canonical.active().orElseThrow(() -> new AdministrationException(
                "config.active.absent", "No active configuration can be rolled back.",
                "Apply a valid configuration first."));
        StoredConfigurationRevision target = history.find(targetRevision).orElseThrow(() ->
                new AdministrationException("config.rollback.unknown", "Unknown configuration revision: "
                        + targetRevision.value(), "Use config history to select an existing applied revision."));
        if (target.status() != ConfigurationApplicationStatus.APPLIED) {
            throw new AdministrationException("config.rollback.not_applied",
                    "Only a successfully applied revision can be selected for rollback.",
                    "Choose an APPLIED revision from configuration history.");
        }
        return createDraft(subject, Optional.of(current.revisionId()), target.compiled().documents(), sourceSurface,
                Optional.of(targetRevision), ConfigurationApplyKind.ROLLBACK);
    }

    public ConfigDraft editScalar(
            PermissionSubject subject,
            UUID draftId,
            String actualPath,
            String replacement) {
        subject.require(PhaseSixPermissions.CONFIG_EDIT);
        Objects.requireNonNull(replacement, "replacement");
        DraftState state = requireOwnedDraft(subject, draftId);
        SchemaNode node = schema.resolve(actualPath).orElseThrow(() -> new AdministrationException(
                "config.path.unknown", "Unknown canonical configuration path: " + actualPath,
                "Use config search before editing."));
        subject.require(node.editPermission());
        ConfigurationPathResolver.ResolvedPath resolved = paths.resolve(actualPath).orElseThrow(() ->
                new AdministrationException("config.path.not_editable",
                        "This schema path is not a lossless scalar edit surface: " + actualPath,
                        "Edit the owning YAML draft or use a structured wizard operation."));
        String source = state.draft().documents().get(resolved.documentName());
        if (source == null) {
            throw new AdministrationException("config.document.missing",
                    "Draft is missing canonical document " + resolved.documentName(),
                    "Restore the document before attempting an edit.");
        }
        validateAllowed(node, replacement);
        LosslessYamlDocument document = LosslessYamlDocument.parse(source);
        LosslessYamlDocument edited = replace(document, resolved, node.type(), replacement);
        LinkedHashMap<String, String> documents = new LinkedHashMap<>(state.draft().documents());
        documents.put(resolved.documentName(), edited.render());
        ConfigDraft updated = new ConfigDraft(state.draft().draftId(), state.draft().baseRevision(), documents,
                state.draft().actor(), state.draft().createdAt());
        if (!drafts.replace(draftId, state, state.withDraft(updated))) {
            throw new AdministrationException("config.draft.concurrent_edit",
                    "The draft changed while this edit was being prepared.",
                    "Review the current draft and retry the edit against its latest version.");
        }
        return updated;
    }

    public CompletionStage<ConfigurationPreview> preview(
            PermissionSubject subject,
            UUID draftId) {
        requirePreviewPermission(subject, draftId);
        DraftState state = requireOwnedDraft(subject, draftId);
        return workflow.prepare(state.draft(), state.remapPlan()).thenApply(candidate -> {
            boolean stale = !activeRevision().equals(state.draft().baseRevision());
            ConfigurationPreview preview = new ConfigurationPreview(draftId, state.version(),
                    state.draft().baseRevision(),
                    state.rollbackSource(), candidate.compiled().contentHash(),
                    candidate.stageRemap().map(StageRemapSnapshot::seal), changedDocuments(state.draft()),
                    candidate.validation(), candidate.stageImpact(), stale);
            drafts.compute(draftId, (ignored, current) -> {
                if (current == null) {
                    throw new AdministrationException("config.draft.cancelled",
                            "The draft was cancelled while its preview was being prepared.",
                            "Create and preview a new draft.");
                }
                if (current.version() != state.version()
                        || !RevisionHasher.hashDocuments(current.draft().documents())
                                .equals(candidate.compiled().contentHash())) {
                    throw new AdministrationException("config.draft.changed_during_prepare",
                            "The draft changed while its preview was being prepared.",
                            "Preview the current draft version again.");
                }
                return current.withPreview(preview, candidate);
            });
            return preview;
        });
    }

    public List<String> listValues(PermissionSubject subject, UUID draftId, String actualPath) {
        subject.require(PhaseSixPermissions.CONFIG_VIEW);
        DraftState state = requireOwnedDraft(subject, draftId);
        SchemaNode node = schema.resolve(actualPath).orElseThrow(() -> new AdministrationException(
                "config.path.unknown", "Unknown canonical configuration path: " + actualPath,
                "Use config search before listing a structure."));
        ConfigurationPathResolver.ResolvedPath resolved = paths.resolve(actualPath).orElseThrow(() ->
                new AdministrationException("config.path.not_listable", "Configuration path cannot be listed.",
                        "Use a schema-owned list or map path."));
        LosslessYamlDocument document = document(state, resolved);
        try {
            return switch (node.type()) {
                case LIST -> document.sequenceScalars(resolved.yamlPath());
                case MAP -> document.mappingKeys(resolved.yamlPath());
                default -> throw new AdministrationException("config.path.not_listable",
                        "Configuration path is not a list or map: " + actualPath,
                        "Use config get/explain for scalar settings.");
            };
        } catch (IllegalArgumentException exception) {
            throw new AdministrationException("config.list.rejected", safeMessage(exception),
                    "Correct the draft structure and validate it before listing values.");
        }
    }

    public ConfigDraft addListValue(
            PermissionSubject subject,
            UUID draftId,
            String actualPath,
            String value) {
        subject.require(PhaseSixPermissions.CONFIG_EDIT);
        DraftState state = requireOwnedDraft(subject, draftId);
        SchemaNode node = requireStructuralNode(subject, actualPath, SchemaValueType.LIST);
        ConfigurationPathResolver.ResolvedPath resolved = paths.resolve(actualPath).orElseThrow();
        LosslessYamlDocument edited;
        try {
            edited = document(state, resolved).appendSequenceString(resolved.yamlPath(), value);
        } catch (IllegalArgumentException exception) {
            throw new AdministrationException("config.add.rejected", safeMessage(exception),
                    "Add a unique schema-valid scalar value to the selected list.");
        }
        return replaceDocument(draftId, state, resolved.documentName(), edited.render(), state.remapPlan());
    }

    public ConfigDraft removeListValue(
            PermissionSubject subject,
            UUID draftId,
            String actualPath,
            String value) {
        subject.require(PhaseSixPermissions.CONFIG_EDIT);
        DraftState state = requireOwnedDraft(subject, draftId);
        requireStructuralNode(subject, actualPath, SchemaValueType.LIST);
        ConfigurationPathResolver.ResolvedPath resolved = paths.resolve(actualPath).orElseThrow();
        LosslessYamlDocument edited;
        try {
            edited = document(state, resolved).removeSequenceString(resolved.yamlPath(), value);
        } catch (IllegalArgumentException exception) {
            throw new AdministrationException("config.remove.rejected", safeMessage(exception),
                    "Remove an existing scalar value from the selected list.");
        }
        return replaceDocument(draftId, state, resolved.documentName(), edited.render(), state.remapPlan());
    }

    public ConfigDraft addStage(
            PermissionSubject subject,
            UUID draftId,
            net.maddkraft.maddprestige.api.id.StageId stageId,
            String displayName,
            Optional<net.maddkraft.maddprestige.api.id.ProviderId> providerId,
            Optional<String> externalGroup) {
        subject.require(PhaseSixPermissions.CONFIG_EDIT);
        DraftState state = requireOwnedDraft(subject, draftId);
        if (displayName == null || displayName.isBlank() || displayName.length() > 128
                || displayName.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Stage display name must contain 1-128 printable characters");
        }
        if (providerId.isPresent() != externalGroup.isPresent()) {
            throw new IllegalArgumentException("External stages require both provider and existing group name");
        }
        String source = state.draft().documents().get("progression.yml");
        if (source == null) {
            throw new AdministrationException("config.document.missing", "Draft has no progression.yml document.",
                    "Restore progression.yml before adding a stage.");
        }
        List<String> lines = new ArrayList<>();
        lines.add("enabled: true");
        lines.add("display-name: " + quoteYaml(displayName));
        if (providerId.isPresent()) {
            lines.add("projection:");
            lines.add("  type: group");
            lines.add("  provider: " + providerId.orElseThrow().value());
            lines.add("  group: " + quoteYaml(externalGroup.orElseThrow()));
        } else {
            lines.add("projection: none");
        }
        try {
            LosslessYamlDocument document = LosslessYamlDocument.parse(source)
                    .appendMappingBlock(YamlPath.document(0).key("stages"), stageId.value(), lines);
            document = document.appendSequenceString(YamlPath.document(0).key("order"), stageId.value());
            return replaceDocument(draftId, state, "progression.yml", document.render(), state.remapPlan());
        } catch (IllegalArgumentException exception) {
            throw new AdministrationException("stage.add.rejected", safeMessage(exception),
                    "Use a unique stage ID and a supported block-style progression document.");
        }
    }

    public ConfigDraft removeStage(
            PermissionSubject subject,
            UUID draftId,
            net.maddkraft.maddprestige.api.id.StageId stageId,
            Optional<net.maddkraft.maddprestige.api.id.StageId> replacement) {
        subject.require(PhaseSixPermissions.CONFIG_EDIT);
        DraftState state = requireOwnedDraft(subject, draftId);
        replacement.ifPresent(value -> {
            if (value.equals(stageId)) {
                throw new AdministrationException("stage.change.remap_invalid",
                        "A deleted stage cannot be its own replacement.",
                        "Select a different enabled ordered stage.");
            }
        });
        String source = state.draft().documents().get("progression.yml");
        if (source == null) {
            throw new AdministrationException("config.document.missing", "Draft has no progression.yml document.",
                    "Restore progression.yml before deleting a stage.");
        }
        try {
            LosslessYamlDocument document = LosslessYamlDocument.parse(source)
                    .removeMappingEntry(YamlPath.document(0).key("stages"), stageId.value());
            document = document.removeSequenceString(YamlPath.document(0).key("order"), stageId.value());
            LinkedHashMap<String, String> editedDocuments = new LinkedHashMap<>(state.draft().documents());
            editedDocuments.put("progression.yml", document.render());
            ConfigDraft edited = new ConfigDraft(state.draft().draftId(), state.draft().baseRevision(),
                    editedDocuments, state.draft().actor(), state.draft().createdAt());
            Optional<StageRemapPlan> plan = replacement
                    .map(value -> mergeRemap(state, stageId, value))
                    .or(() -> state.remapPlan());
            plan.ifPresent(value -> validateDraftRemap(edited, value));
            return replaceDocument(draftId, state, "progression.yml", document.render(), plan);
        } catch (IllegalArgumentException exception) {
            throw new AdministrationException("stage.remove.rejected", safeMessage(exception),
                    "Select an existing stage and provide a valid replacement when references exist.");
        }
    }

    public ConfigDraft selectStageRemap(
            PermissionSubject subject,
            UUID draftId,
            net.maddkraft.maddprestige.api.id.StageId source,
            net.maddkraft.maddprestige.api.id.StageId replacement) {
        DraftState state = requireOwnedDraft(subject, draftId);
        requireApplyPermission(subject, state.requiredKind());
        if (source.equals(replacement)) {
            throw new AdministrationException("stage.change.remap_invalid",
                    "A missing stage cannot be its own replacement.",
                    "Select a different enabled ordered stage present in the candidate configuration.");
        }
        StageRemapPlan plan = mergeRemap(state, source, replacement);
        validateDraftRemap(state.draft(), plan);
        DraftState updated = state.withRemap(Optional.of(plan));
        if (!drafts.replace(draftId, state, updated)) {
            throw new AdministrationException("config.draft.concurrent_edit",
                    "The draft changed while its stage remap was being selected.",
                    "Review the current draft and select the replacement again.");
        }
        return updated.draft();
    }

    public ConfigDraft removeStageRemap(
            PermissionSubject subject,
            UUID draftId,
            net.maddkraft.maddprestige.api.id.StageId source) {
        DraftState state = requireOwnedDraft(subject, draftId);
        requireApplyPermission(subject, state.requiredKind());
        StageRemapPlan current = state.remapPlan().orElseThrow(() -> new AdministrationException(
                "stage.change.remap_missing", "The draft has no stage-remap mapping to remove.",
                "Select a replacement mapping before attempting to remove it."));
        Map<net.maddkraft.maddprestige.api.id.StageId, net.maddkraft.maddprestige.api.id.StageId> remaining =
                current.withoutMapping(source);
        if (remaining.size() == current.mappings().size()) {
            throw new AdministrationException("stage.change.remap_source_missing",
                    "The draft has no remap for source stage " + source.value() + ".",
                    "Review the current draft remap before removing a mapping.");
        }
        Optional<StageRemapPlan> replacement = remaining.isEmpty() ? Optional.empty() : Optional.of(
                new StageRemapPlan(draftId + ":v" + Math.addExact(state.version(), 1), remaining));
        replacement.ifPresent(value -> validateDraftRemap(state.draft(), value));
        DraftState updated = state.withRemap(replacement);
        if (!drafts.replace(draftId, state, updated)) {
            throw new AdministrationException("config.draft.concurrent_edit",
                    "The draft changed while its stage remap was being removed.",
                    "Review the current draft and remove the mapping again.");
        }
        return updated.draft();
    }

    public CompletionStage<StoredConfigurationRevision> applyDraft(
            PermissionSubject subject,
            UUID draftId,
            Optional<ConfigRevisionId> expectedBase,
            Set<String> acknowledgements,
            String reason) {
        requireApplyKind(subject, draftId, ConfigurationApplyKind.NORMAL);
        rejectCallerAcknowledgements(acknowledgements);
        return apply(subject, draftId, expectedBase, acknowledgements, reason, ConfigurationApplyKind.NORMAL);
    }

    public CompletionStage<StoredConfigurationRevision> applyRollback(
            PermissionSubject subject,
            UUID draftId,
            Optional<ConfigRevisionId> expectedBase,
            Set<String> acknowledgements,
            String reason) {
        requireApplyKind(subject, draftId, ConfigurationApplyKind.ROLLBACK);
        rejectCallerAcknowledgements(acknowledgements);
        DraftState state = requireOwnedDraft(subject, draftId);
        if (state.rollbackSource().isEmpty()) {
            throw new AdministrationException("config.rollback.not_prepared", "Draft is not a rollback candidate.",
                    "Begin rollback from an applied historical revision first.");
        }
        return apply(subject, draftId, expectedBase, acknowledgements, reason, ConfigurationApplyKind.ROLLBACK);
    }

    public CompletionStage<StoredConfigurationRevision> applySetup(
            PermissionSubject subject,
            UUID draftId,
            Set<String> acknowledgements,
            String reason) {
        requireApplyKind(subject, draftId, ConfigurationApplyKind.SETUP);
        rejectCallerAcknowledgements(acknowledgements);
        DraftState state = requireOwnedDraft(subject, draftId);
        if (state.draft().baseRevision().isPresent() || state.rollbackSource().isPresent()) {
            throw new AdministrationException("setup.draft.invalid",
                    "Setup may apply only a first revision with no active base.",
                    "Use the normal configuration or rollback workflow for an existing deployment.");
        }
        return apply(subject, draftId, Optional.empty(), acknowledgements, reason, ConfigurationApplyKind.SETUP);
    }

    public List<StoredConfigurationRevision> history(PermissionSubject subject, int limit) {
        subject.require(PhaseSixPermissions.CONFIG_VIEW);
        if (limit < 1 || limit > MAX_HISTORY_LIMIT) {
            throw new IllegalArgumentException("History limit must be between 1 and " + MAX_HISTORY_LIMIT);
        }
        return history.recent(limit);
    }

    public Optional<ActiveConfiguration> active(PermissionSubject subject) {
        subject.require(PhaseSixPermissions.CONFIG_VIEW);
        return canonical.active();
    }

    public boolean activeConfigurationPresentForSetup(PermissionSubject subject) {
        subject.require(PhaseSixPermissions.SETUP);
        return canonical.active().isPresent();
    }

    public void discardDraft(PermissionSubject subject, UUID draftId) {
        DraftState state = requireOwnedDraft(subject, draftId);
        subject.require(state.requiredKind() == ConfigurationApplyKind.NORMAL
                ? PhaseSixPermissions.CONFIG_EDIT : permission(state.requiredKind()));
        if (!drafts.remove(draftId, state)) {
            throw new AdministrationException("config.draft.concurrent_change",
                    "The draft changed while cancellation was requested.",
                    "Review the current draft and cancel its latest version.");
        }
    }

    public PreparedConfigurationAcknowledgement prepareAcknowledgement(
            PermissionSubject subject,
            UUID draftId) {
        DraftState state = requireOwnedDraft(subject, draftId);
        requireApplyPermission(subject, state.requiredKind());
        ConfigurationPreview preview = state.preview().orElseThrow(() -> new AdministrationException(
                "config.preview.required", "Configuration must be previewed before acknowledgement.",
                "Preview the exact draft and review every high-risk finding first."));
        List<ValidationFinding> required = preview.validation().findings().stream()
                .filter(finding -> finding.severity() == ValidationSeverity.ACKNOWLEDGEMENT_REQUIRED).toList();
        if (preview.validation().hasErrors()) {
            throw new AdministrationException("config.validation.blocked",
                    "A blocked configuration cannot receive acknowledgement authority.",
                    "Correct every validation error and preview again.");
        }
        if (required.isEmpty()) {
            throw new AdministrationException("config.acknowledgement.not_required",
                    "This exact preview has no high-risk findings requiring acknowledgement.",
                    "Use the normal apply command for this preview.");
        }
        if (!activeRevision().equals(preview.baseRevision())) {
            throw new AdministrationException("config.revision.stale",
                    "The active configuration changed after this draft was previewed.",
                    "Start or preview a draft from the current revision.");
        }
        pruneExpiredAuthorities();
        UUID id = UUID.randomUUID();
        Instant expiresAt = Instant.now(clock).plus(ACKNOWLEDGEMENT_LIFETIME);
        ContentHash findingSeal = findingSeal(preview.validation().findings());
        Set<String> codes = required.stream().map(ValidationFinding::code)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        acknowledgements.put(id, new ConfigurationAcknowledgement(subject.actor(), draftId, state.version(),
                preview.candidateHash(), preview.baseRevision(), state.requiredKind(), findingSeal, codes, expiresAt));
        return new PreparedConfigurationAcknowledgement(id, draftId, state.version(), preview.candidateHash(),
                state.requiredKind(), required, expiresAt);
    }

    /** Reconciles durable destructive-stage authority from server-owned history, pointer and runtime evidence. */
    public synchronized int recoverConfigurationStageTransitions() {
        Optional<ConfigRevisionId> runtimeRevision = activeRevision();
        Optional<ConfigRevisionId> pointerRevision = snapshots.currentRevision();
        int resolved = 0;
        for (ConfigurationStageTransitionState transition : workflow.stageTransitions(1000)) {
            if (!transition.scopeComplete()) {
                retainTransitionRecovery(transition.configurationRevision(),
                        "Legacy transition has no provable complete unsafe-stage scope.");
                continue;
            }
            Optional<StoredConfigurationRevision> owner = history.find(transition.configurationRevision());
            if (owner.isEmpty()) {
                retainTransitionRecovery(transition.configurationRevision(),
                        "Configuration transition has no durable history owner.");
                continue;
            }
            StoredConfigurationRevision revision = owner.orElseThrow();
            if (!pointerRevision.equals(runtimeRevision)) {
                retainTransitionRecovery(transition.configurationRevision(),
                        "Active pointer and runtime revisions disagree; reservation remains fail-closed.");
                continue;
            }
            if (revision.status() == ConfigurationApplicationStatus.APPLIED) {
                if (runtimeRevision.equals(Optional.of(transition.configurationRevision()))) {
                    workflow.markStageTransitionApplied(transition.configurationRevision(), Instant.now(clock));
                    resolved++;
                } else {
                    retainTransitionRecovery(transition.configurationRevision(),
                            "APPLIED history contradicts pointer/runtime authority; reservation remains fail-closed.");
                }
                continue;
            }
            if (revision.status() == ConfigurationApplicationStatus.FAILED) {
                if (runtimeRevision.equals(transition.priorRevision())) {
                    workflow.markStageTransitionFailedSafe(transition.configurationRevision(),
                            "Recovered terminal FAILED history with coherent fallback pointer/runtime authority.",
                            Instant.now(clock));
                    resolved++;
                } else {
                    retainTransitionRecovery(transition.configurationRevision(),
                            "FAILED history contradicts fallback pointer/runtime authority; reservation remains "
                                    + "fail-closed.");
                }
                continue;
            }
            if (runtimeRevision.equals(Optional.of(transition.configurationRevision()))) {
                StoredConfigurationRevision applied = revision.withOutcome(ConfigurationApplicationStatus.APPLIED,
                        Optional.of(Instant.now(clock)), Optional.empty());
                history.replaceOutcome(applied);
                workflow.markStageTransitionApplied(transition.configurationRevision(), Instant.now(clock));
                resolved++;
            } else if (runtimeRevision.equals(transition.priorRevision())) {
                StoredConfigurationRevision failed = revision.withOutcome(ConfigurationApplicationStatus.FAILED,
                        Optional.empty(), Optional.of(
                                "Recovered before candidate authority; prior pointer/runtime remain coherent."));
                history.replaceOutcome(failed);
                workflow.markStageTransitionFailedSafe(transition.configurationRevision(),
                        "Recovered prior pointer/runtime authority with fallback-safe remap targets.",
                        Instant.now(clock));
                resolved++;
            } else {
                retainTransitionRecovery(transition.configurationRevision(),
                        "Neither candidate nor prior revision is the coherent pointer/runtime authority.");
            }
        }
        return resolved;
    }

    public ConfigurationApplyKind requiredApplyKind(PermissionSubject subject, UUID draftId) {
        DraftState state = requireOwnedDraft(subject, draftId);
        requirePreviewPermission(subject, draftId);
        return state.requiredKind();
    }

    public ConfigurationApplyKind acknowledgementApplyKind(PermissionSubject subject, UUID acknowledgementId) {
        ConfigurationAcknowledgement authority = acknowledgements.get(
                Objects.requireNonNull(acknowledgementId, "acknowledgement ID"));
        if (authority == null || !Instant.now(clock).isBefore(authority.expiresAt())) {
            throw new AdministrationException("config.acknowledgement.unknown",
                    "Unknown, expired, or already-used configuration acknowledgement.",
                    "Preview the draft and request a fresh server-issued acknowledgement.");
        }
        if (!authority.actor().equals(subject.actor())) {
            throw new AdministrationException("config.acknowledgement.actor_mismatch",
                    "Configuration acknowledgement cannot be transferred to another actor.",
                    "The intended administrator must preview and acknowledge the draft.");
        }
        requireApplyPermission(subject, authority.kind());
        return authority.kind();
    }

    public CompletionStage<StoredConfigurationRevision> confirmAcknowledgement(
            PermissionSubject subject,
            UUID acknowledgementId,
            String reason) {
        Objects.requireNonNull(subject, "subject");
        UUID id = Objects.requireNonNull(acknowledgementId, "acknowledgement ID");
        ConfigurationAcknowledgement authority = acknowledgementForConfirmation(id);
        if (!authority.actor().equals(subject.actor())) {
            throw new AdministrationException("config.acknowledgement.actor_mismatch",
                    "Configuration acknowledgement cannot be transferred to another actor.",
                    "The intended administrator must preview and acknowledge the draft.");
        }
        if (!Instant.now(clock).isBefore(authority.expiresAt())) {
            acknowledgements.remove(id, authority);
            throw new AdministrationException("config.acknowledgement.expired",
                    "Configuration acknowledgement expired.",
                    "Preview current state and request a new acknowledgement.");
        }
        requireApplyPermission(subject, authority.kind());
        DraftState state = requireOwnedDraft(subject, authority.draftId());
        if (state.requiredKind() != authority.kind()
                || state.version() != authority.draftVersion()
                || !RevisionHasher.hashDocuments(state.draft().documents()).equals(authority.candidateHash())
                || !activeRevision().equals(authority.baseRevision())
                || state.preview().isEmpty()
                || !findingSeal(state.preview().orElseThrow().validation().findings()).equals(authority.findingSeal())) {
            acknowledgements.remove(id, authority);
            throw new AdministrationException("config.acknowledgement.stale",
                    "Draft, active revision, or exact finding set changed after acknowledgement was prepared.",
                    "Preview and acknowledge the current candidate again.");
        }
        consumeAcknowledgement(id, authority);
        return apply(subject, authority.draftId(), authority.baseRevision(), authority.acknowledgedCodes(), reason,
                authority.kind(), Optional.of(authority.findingSeal()));
    }

    private synchronized ConfigurationAcknowledgement acknowledgementForConfirmation(UUID id) {
        ConfigurationAcknowledgement authority = acknowledgements.get(id);
        if (authority != null) {
            return authority;
        }
        if (consumedAcknowledgements.containsKey(id)) {
            throw new AdministrationException("config.acknowledgement.already_used",
                    "This configuration acknowledgement was consumed by another request.",
                    "Preview and request a fresh single-use acknowledgement.");
        }
        throw new AdministrationException("config.acknowledgement.unknown",
                "Unknown or expired configuration acknowledgement.",
                "Preview the draft and request a fresh server-issued acknowledgement.");
    }

    private synchronized void consumeAcknowledgement(UUID id, ConfigurationAcknowledgement authority) {
        if (!acknowledgements.remove(id, authority)) {
            throw new AdministrationException("config.acknowledgement.already_used",
                    "This configuration acknowledgement was consumed by another request.",
                    "Preview and request a fresh single-use acknowledgement.");
        }
        consumedAcknowledgements.put(id, authority.expiresAt());
    }

    private CompletionStage<StoredConfigurationRevision> apply(
            PermissionSubject subject,
            UUID draftId,
            Optional<ConfigRevisionId> expectedBase,
            Set<String> acknowledgements,
            String reason,
            ConfigurationApplyKind kind) {
        return apply(subject, draftId, expectedBase, acknowledgements, reason, kind, Optional.empty());
    }

    private CompletionStage<StoredConfigurationRevision> apply(
            PermissionSubject subject,
            UUID draftId,
            Optional<ConfigRevisionId> expectedBase,
            Set<String> acknowledgements,
            String reason,
            ConfigurationApplyKind kind,
            Optional<ContentHash> expectedFindingSeal) {
        requireApplyKind(subject, draftId, kind);
        if (reason == null || reason.isBlank() || reason.length() > 512) {
            throw new IllegalArgumentException("Configuration apply reason must contain 1-512 characters");
        }
        DraftState state = requireOwnedDraft(subject, draftId);
        ConfigurationPreview lastPreview = state.preview().orElseThrow(() -> new AdministrationException(
                "config.preview.required", "Configuration must be previewed before apply.",
                "Run config diff/validate and review the exact candidate first."));
        if (!lastPreview.candidateHash().equals(RevisionHasher.hashDocuments(state.draft().documents()))) {
            throw new AdministrationException("config.preview.stale", "Draft changed after its last preview.",
                    "Preview the draft again before applying it.");
        }
        if (lastPreview.draftVersion() != state.version()) {
            throw new AdministrationException("config.preview.stale", "Draft changed after its last preview.",
                    "Preview the current draft version before applying it.");
        }
        PhaseSixConfigurationCandidate previewedCandidate = state.candidate().orElseThrow(() ->
                new AdministrationException("config.preview.candidate_missing",
                        "The exact preview candidate is no longer available.",
                        "Preview the current draft again before applying it."));
        Optional<StageRemapPlan> remapPlan = previewedCandidate.stageRemap().map(StageRemapSnapshot::plan);
        return workflow.prepare(state.draft(), remapPlan).thenApply(candidate -> {
            if (!candidate.stageRemap().map(StageRemapSnapshot::seal)
                    .equals(previewedCandidate.stageRemap().map(StageRemapSnapshot::seal))) {
                throw new AdministrationException("stage.change.remap_snapshot_stale",
                        "Persisted player stage references changed after the remap preview.",
                        "Preview the exact replacement migration again before applying it.");
            }
            if (expectedFindingSeal.isPresent()
                    && !expectedFindingSeal.orElseThrow().equals(findingSeal(candidate.validation().findings()))) {
                throw new AdministrationException("config.acknowledgement.findings_changed",
                        "Validation findings changed after acknowledgement was prepared.",
                        "Review and acknowledge the current exact finding set.");
            }
            return finalizeApply(subject, state, candidate, expectedBase, acknowledgements, reason);
        });
    }

    private synchronized StoredConfigurationRevision finalizeApply(
            PermissionSubject subject,
            DraftState state,
            PhaseSixConfigurationCandidate candidate,
            Optional<ConfigRevisionId> expectedBase,
            Set<String> acknowledgements,
            String reason) {
        DraftState live = drafts.get(state.draft().draftId());
        if (live == null) {
            throw new AdministrationException("config.draft.cancelled",
                    "The draft was cancelled while apply validation was running.",
                    "Create and preview a new draft; cancelled authority is never revived.");
        }
        if (live.version() != state.version()
                || !RevisionHasher.hashDocuments(live.draft().documents()).equals(candidate.compiled().contentHash())
                || !live.draft().actor().equals(subject.actor())) {
            throw new AdministrationException("config.draft.changed_during_apply",
                    "The draft changed or was superseded while apply validation was running.",
                    "Review and preview the current draft version; the older candidate was not activated.");
        }
        requireApplyPermission(subject, live.requiredKind());
        DraftState claimed = live.claimForApply();
        if (!drafts.replace(state.draft().draftId(), live, claimed)) {
            throw new AdministrationException("config.draft.changed_during_apply",
                    "The draft changed while its apply authority was being claimed.",
                    "Review and preview the current draft version; the older candidate was not activated.");
        }
        Optional<ConfigRevisionId> current = activeRevision();
        if (!current.equals(expectedBase) || !current.equals(state.draft().baseRevision())) {
            drafts.replace(state.draft().draftId(), claimed, state);
            throw new AdministrationException("config.revision.stale",
                    "The active configuration changed after this draft was created.",
                    "Start a new draft from the current revision and reapply the intended edits.");
        }
        if (!candidate.validation().canApply(acknowledgements)) {
            drafts.replace(state.draft().draftId(), claimed, state);
            throw new AdministrationException("config.validation.blocked",
                    "Configuration contains errors or unacknowledged high-risk changes.",
                    "Correct every error and explicitly acknowledge each required finding before apply.");
        }
        ConfigRevisionId revisionId = new ConfigRevisionId("r_" + UUID.randomUUID().toString().replace("-", ""));
        Instant now = Instant.now(clock);
        String diffSummary = String.join(",", changedDocuments(state.draft()));
        StoredConfigurationRevision attempted = new StoredConfigurationRevision(revisionId, current,
                state.rollbackSource(), candidate.compiled(), subject.actor(), state.sourceSurface(), reason,
                candidate.validation(), diffSummary, ConfigurationApplicationStatus.ATTEMPTED, now,
                Optional.empty(), Optional.empty());
        PreparedConfigurationSnapshot prepared = null;
        Optional<ConfigurationStageTransitionExecution> stageTransition = Optional.empty();
        boolean configurationActivated = false;
        try {
            history.append(attempted);
            try {
                prepared = snapshots.prepare(revisionId, candidate.compiled());
            } catch (RuntimeException exception) {
                finalizeFailure(attempted, exception);
                throw new AdministrationException("config.snapshot.prepare_failed",
                        "Could not prepare a durable configuration snapshot.",
                        "The active configuration is unchanged; check filesystem/storage health and retry.");
            }
            try {
                stageTransition = workflow.beginStageTransition(revisionId, current, candidate, subject.actor(),
                        reason, Instant.now(clock));
            } catch (RuntimeException exception) {
                finalizeFailure(attempted, exception);
                throw new AdministrationException("stage.change.transition_stale",
                        "The destructive stage transition could not reserve and revalidate its complete "
                                + "unsafe-stage scope.",
                        "No configuration was activated; preview current references and operation state, then retry.");
            }
            try {
                prepared.activate();
            } catch (RuntimeException exception) {
                finalizeFailure(attempted, exception);
                markTransitionFailedSafe(stageTransition, exception);
                throw new AdministrationException("config.snapshot.activate_failed",
                        "Could not atomically select the prepared configuration snapshot.",
                        "The prior active pointer and runtime configuration remain authoritative.");
            }
            try {
                workflow.apply(revisionId, candidate, acknowledgements, prepared.backup());
                configurationActivated = true;
            } catch (RuntimeException exception) {
                boolean previousPointerRestored = false;
                try {
                    prepared.restorePrevious();
                    previousPointerRestored = true;
                } catch (RuntimeException restoreFailure) {
                    exception.addSuppressed(restoreFailure);
                }
                if (previousPointerRestored) {
                    finalizeFailure(attempted, exception);
                    markTransitionFailedSafe(stageTransition, exception);
                } else if (stageTransition.isPresent()) {
                    markTransitionNeedsReconciliation(stageTransition, exception);
                } else {
                    finalizeFailure(attempted, exception);
                }
                throw new AdministrationException("config.apply.failed", safeMessage(exception),
                        previousPointerRestored
                                ? "The runtime rejected the candidate and the prior pointer was restored safely."
                                : "The prior pointer could not be restored; destructive-stage authority remains "
                                        + "reserved and requires explicit reconciliation.");
            }
            StoredConfigurationRevision applied = attempted.withOutcome(ConfigurationApplicationStatus.APPLIED,
                    Optional.of(Instant.now(clock)), Optional.empty());
            try {
                history.replaceOutcome(applied);
            } catch (RuntimeException exception) {
                throw new AdministrationException("config.history.finalize_failed",
                        "Configuration revision " + revisionId.value()
                                + " is active, but its durable history outcome remains ATTEMPTED.",
                        "Do not retry the stale draft; run doctor and reconcile the attempted history outcome.");
            }
            if (stageTransition.isPresent()) {
                try {
                    workflow.markStageTransitionApplied(revisionId, Instant.now(clock));
                } catch (RuntimeException exception) {
                    throw new AdministrationException("stage.change.transition_reconciliation_pending",
                            "Configuration is active, but its unsafe-stage reservation still requires cleanup.",
                            "Do not retry the stale draft; run configuration-transition recovery for revision "
                                    + revisionId.value() + ".");
                }
            }
            return applied;
        } catch (AdministrationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AdministrationException("config.apply.failed", safeMessage(exception),
                    "The last known-good configuration remains authoritative; inspect history and doctor output.");
        } finally {
            if (configurationActivated) {
                drafts.remove(state.draft().draftId(), claimed);
            } else {
                drafts.replace(state.draft().draftId(), claimed, state);
            }
            if (prepared != null) {
                prepared.close();
            }
        }
    }

    private void finalizeFailure(StoredConfigurationRevision attempted, RuntimeException failure) {
        StoredConfigurationRevision failed = attempted.withOutcome(ConfigurationApplicationStatus.FAILED,
                Optional.empty(), Optional.of(safeMessage(failure)));
        try {
            history.replaceOutcome(failed);
        } catch (RuntimeException historyFailure) {
            failure.addSuppressed(historyFailure);
        }
    }

    private void markTransitionFailedSafe(
            Optional<ConfigurationStageTransitionExecution> execution,
            RuntimeException failure) {
        execution.ifPresent(value -> {
            try {
                workflow.markStageTransitionFailedSafe(value.configurationRevision(),
                        "Every remap target remains valid under prior authority and configuration activation "
                                + "failed safely: " + safeMessage(failure),
                        Instant.now(clock));
            } catch (RuntimeException statusFailure) {
                failure.addSuppressed(statusFailure);
            }
        });
    }

    private void markTransitionNeedsReconciliation(
            Optional<ConfigurationStageTransitionExecution> execution,
            RuntimeException failure) {
        execution.ifPresent(value -> {
            try {
                workflow.markStageTransitionNeedsReconciliation(value.configurationRevision(),
                        "Pointer/runtime authority could not be restored coherently after activation failure: "
                                + safeMessage(failure), Instant.now(clock));
            } catch (RuntimeException statusFailure) {
                failure.addSuppressed(statusFailure);
            }
        });
    }

    private void retainTransitionRecovery(ConfigRevisionId revision, String detail) {
        workflow.markStageTransitionNeedsReconciliation(revision, detail, Instant.now(clock));
    }

    private UUID createDraft(
            PermissionSubject subject,
            Optional<ConfigRevisionId> base,
            Map<String, String> documents,
            String sourceSurface,
            Optional<ConfigRevisionId> rollbackSource,
            ConfigurationApplyKind requiredKind) {
        Objects.requireNonNull(sourceSurface, "source surface");
        if (sourceSurface.isBlank() || sourceSurface.length() > 64) {
            throw new IllegalArgumentException("Source surface must contain 1-64 characters");
        }
        ConfigDraft draft = new ConfigDraft(UUID.randomUUID(), base, documents, subject.actor(), Instant.now(clock));
        drafts.put(draft.draftId(), new DraftState(draft, 1, sourceSurface, rollbackSource, requiredKind,
                Optional.empty(), Optional.empty(), Optional.empty(), false));
        return draft.draftId();
    }

    private ConfigDraft replaceDocument(
            UUID draftId,
            DraftState state,
            String documentName,
            String content,
            Optional<StageRemapPlan> remapPlan) {
        LinkedHashMap<String, String> documents = new LinkedHashMap<>(state.draft().documents());
        documents.put(documentName, content);
        ConfigDraft replacement = new ConfigDraft(state.draft().draftId(), state.draft().baseRevision(), documents,
                state.draft().actor(), state.draft().createdAt());
        if (!drafts.replace(draftId, state, state.withDraft(replacement, remapPlan))) {
            throw new AdministrationException("config.draft.concurrent_edit",
                    "The draft changed while this structural edit was being prepared.",
                    "Review the current draft and retry against its latest version.");
        }
        return replacement;
    }

    private LosslessYamlDocument document(
            DraftState state,
            ConfigurationPathResolver.ResolvedPath resolved) {
        String source = state.draft().documents().get(resolved.documentName());
        if (source == null) {
            throw new AdministrationException("config.document.missing",
                    "Draft is missing canonical document " + resolved.documentName(),
                    "Restore the document before attempting a structural edit.");
        }
        return LosslessYamlDocument.parse(source);
    }

    private SchemaNode requireStructuralNode(
            PermissionSubject subject,
            String path,
            SchemaValueType expected) {
        SchemaNode node = schema.resolve(path).orElseThrow(() -> new AdministrationException(
                "config.path.unknown", "Unknown canonical configuration path: " + path,
                "Use config search before editing."));
        subject.require(node.editPermission());
        if (node.type() != expected) {
            throw new AdministrationException("config.path.type_mismatch",
                    "Configuration path is " + node.type() + ", not " + expected + ": " + path,
                    "Use the operation that matches the schema-owned value type.");
        }
        return node;
    }

    private static String quoteYaml(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    private void requireApplyPermission(PermissionSubject subject, ConfigurationApplyKind kind) {
        subject.require(permission(kind));
    }

    private void requireApplyKind(
            PermissionSubject subject,
            UUID draftId,
            ConfigurationApplyKind requestedKind) {
        DraftState state = requireOwnedDraft(subject, draftId);
        requireMatchingKind(state, requestedKind);
        requireApplyPermission(subject, state.requiredKind());
    }

    private static void requireMatchingKind(DraftState state, ConfigurationApplyKind requestedKind) {
        if (state.requiredKind() != requestedKind) {
            throw new AdministrationException("config.apply.kind_mismatch",
                    "Draft requires " + state.requiredKind() + " application authority, not " + requestedKind + ".",
                    "Use the server-owned " + state.requiredKind() + " apply flow for this exact draft.");
        }
    }

    private static void rejectCallerAcknowledgements(Set<String> acknowledgements) {
        Objects.requireNonNull(acknowledgements, "acknowledgements");
        if (!acknowledgements.isEmpty()) {
            throw new AdministrationException("config.acknowledgement.server_authority_required",
                    "Caller-supplied validation codes cannot authorize a high-risk configuration.",
                    "Request a server-issued acknowledgement for the exact preview, then confirm its token.");
        }
    }

    private static String permission(ConfigurationApplyKind kind) {
        return switch (kind) {
            case NORMAL -> PhaseSixPermissions.CONFIG_APPLY;
            case ROLLBACK -> PhaseSixPermissions.CONFIG_ROLLBACK;
            case SETUP -> PhaseSixPermissions.SETUP;
        };
    }

    private void pruneExpiredAuthorities() {
        Instant now = Instant.now(clock);
        acknowledgements.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
        consumedAcknowledgements.entrySet().removeIf(entry -> !now.isBefore(entry.getValue()));
        drafts.entrySet().removeIf(entry ->
                !now.isBefore(entry.getValue().draft().createdAt().plus(DRAFT_LIFETIME)));
    }

    private static ContentHash findingSeal(List<ValidationFinding> findings) {
        String canonical = findings.stream().sorted(Comparator.comparing(ValidationFinding::code)
                .thenComparing(ValidationFinding::path).thenComparing(value -> value.severity().name())
                .thenComparing(ValidationFinding::explanation).thenComparing(ValidationFinding::consequence)
                .thenComparing(ValidationFinding::remediation)).map(finding -> finding.code() + "|"
                        + finding.severity() + "|" + finding.path() + "|" + finding.explanation() + "|"
                        + finding.consequence() + "|" + finding.remediation())
                .collect(java.util.stream.Collectors.joining("\n"));
        return RevisionHasher.hashText(canonical);
    }

    private DraftState requireOwnedDraft(PermissionSubject subject, UUID draftId) {
        DraftState state = drafts.get(Objects.requireNonNull(draftId, "draft ID"));
        if (state == null) {
            throw new AdministrationException("config.draft.unknown", "Unknown or expired configuration draft.",
                    "Create a new draft from the current active revision.");
        }
        if (!Instant.now(clock).isBefore(state.draft().createdAt().plus(DRAFT_LIFETIME))) {
            drafts.remove(draftId, state);
            throw new AdministrationException("config.draft.expired", "Configuration draft expired.",
                    "Create a new draft from the current active revision.");
        }
        if (!state.draft().actor().equals(subject.actor())) {
            throw new AdministrationException("config.draft.owner_mismatch",
                    "A draft can only be changed or confirmed by its creating actor.",
                    "Create a separate draft for this administrator.");
        }
        if (state.applying()) {
            throw new AdministrationException("config.draft.apply_in_progress",
                    "This exact draft is already being applied.",
                    "Wait for its current apply outcome; do not edit or submit it concurrently.");
        }
        return state;
    }

    private void requirePreviewPermission(PermissionSubject subject, UUID draftId) {
        DraftState state = requireOwnedDraft(subject, draftId);
        subject.require(state.requiredKind() == ConfigurationApplyKind.NORMAL
                ? PhaseSixPermissions.CONFIG_VIEW : permission(state.requiredKind()));
    }

    private Optional<ConfigRevisionId> activeRevision() {
        return canonical.active().map(ActiveConfiguration::revisionId);
    }

    private List<String> changedDocuments(ConfigDraft draft) {
        Map<String, String> before = canonical.active().map(value -> value.compiled().documents()).orElse(Map.of());
        ArrayList<String> changed = new ArrayList<>();
        java.util.stream.Stream.concat(before.keySet().stream(), draft.documents().keySet().stream()).distinct()
                .filter(name -> !Objects.equals(before.get(name), draft.documents().get(name)))
                .forEach(changed::add);
        changed.sort(Comparator.naturalOrder());
        return List.copyOf(changed);
    }

    private static void validateAllowed(SchemaNode node, String replacement) {
        if (!node.allowedValues().staticValues().isEmpty()
                && node.allowedValues().staticValues().stream().noneMatch(replacement::equalsIgnoreCase)) {
            throw new AdministrationException("config.value.not_allowed",
                    "Value is not allowed for " + node.canonicalPath() + ": " + replacement,
                    "Use one of: " + String.join(", ", node.allowedValues().staticValues()));
        }
    }

    private static LosslessYamlDocument replace(
            LosslessYamlDocument document,
            ConfigurationPathResolver.ResolvedPath path,
            SchemaValueType type,
            String replacement) {
        try {
            return switch (type) {
                case BOOLEAN -> {
                    if (!replacement.equalsIgnoreCase("true") && !replacement.equalsIgnoreCase("false")) {
                        throw new IllegalArgumentException("Expected true or false");
                    }
                    yield document.replaceBoolean(path.yamlPath(), Boolean.parseBoolean(replacement));
                }
                case INTEGER -> {
                    if (!replacement.matches("-?(0|[1-9][0-9]*)")) {
                        throw new IllegalArgumentException("Expected a canonical whole number");
                    }
                    yield document.replaceDecimal(path.yamlPath(), replacement);
                }
                case DECIMAL -> document.replaceDecimal(path.yamlPath(), replacement);
                case STRING, ENUM, DURATION, SECRET -> document.replaceString(path.yamlPath(), replacement);
                case LIST, MAP -> throw new IllegalArgumentException("Structured values require a dedicated editor");
            };
        } catch (IllegalArgumentException exception) {
            throw new AdministrationException("config.edit.rejected", safeMessage(exception),
                    "Correct the value or use a structured wizard/editor that preserves YAML presentation.");
        }
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static StageRemapPlan mergeRemap(
            DraftState state,
            net.maddkraft.maddprestige.api.id.StageId source,
            net.maddkraft.maddprestige.api.id.StageId replacement) {
        String revision = state.draft().draftId() + ":v" + Math.addExact(state.version(), 1);
        return state.remapPlan().map(plan -> plan.withMapping(revision, source, replacement))
                .orElseGet(() -> new StageRemapPlan(revision, Map.of(source, replacement)));
    }

    private static void validateDraftRemap(ConfigDraft draft, StageRemapPlan plan) {
        var compilation = new StageConfigurationCompiler().compile(new ConfigCompiler().compile(draft));
        var configuration = compilation.configuration().orElseThrow(() -> new AdministrationException(
                "stage.change.remap_candidate_invalid",
                "The candidate progression document cannot be compiled for remap selection.",
                "Correct progression.yml before selecting stage replacements."));
        for (var mapping : plan.mappings().entrySet()) {
            if (configuration.stages().containsKey(mapping.getKey())) {
                throw new AdministrationException("stage.change.remap_source_present",
                        "Remap source stage " + mapping.getKey().value() + " still exists in the candidate.",
                        "Remove the source stage before selecting its persisted-reference replacement.");
            }
            var target = configuration.stages().get(mapping.getValue());
            if (target == null || !target.enabled() || !configuration.order().contains(mapping.getValue())) {
                throw new AdministrationException("stage.change.remap_target_missing",
                        "Remap target stage " + mapping.getValue().value()
                                + " is absent, disabled, or unordered in the candidate.",
                        "Select an enabled ordered stage present in the candidate configuration.");
            }
        }
    }

    private record DraftState(
            ConfigDraft draft,
            long version,
            String sourceSurface,
            Optional<ConfigRevisionId> rollbackSource,
            ConfigurationApplyKind requiredKind,
            Optional<ConfigurationPreview> preview,
            Optional<PhaseSixConfigurationCandidate> candidate,
            Optional<StageRemapPlan> remapPlan,
            boolean applying) {
        private DraftState {
            draft = Objects.requireNonNull(draft, "draft");
            if (version < 1) {
                throw new IllegalArgumentException("Draft version must be positive");
            }
            sourceSurface = Objects.requireNonNull(sourceSurface, "source surface");
            rollbackSource = Objects.requireNonNull(rollbackSource, "rollback source");
            requiredKind = Objects.requireNonNull(requiredKind, "required apply kind");
            preview = Objects.requireNonNull(preview, "preview");
            candidate = Objects.requireNonNull(candidate, "candidate");
            remapPlan = Objects.requireNonNull(remapPlan, "remap plan");
        }

        private DraftState withDraft(ConfigDraft replacement) {
            return withDraft(replacement, remapPlan);
        }

        private DraftState withDraft(ConfigDraft replacement, Optional<StageRemapPlan> replacementRemap) {
            return new DraftState(replacement, Math.addExact(version, 1), sourceSurface, rollbackSource, requiredKind,
                    Optional.empty(), Optional.empty(), replacementRemap, false);
        }

        private DraftState withRemap(Optional<StageRemapPlan> replacementRemap) {
            return new DraftState(draft, Math.addExact(version, 1), sourceSurface, rollbackSource, requiredKind,
                    Optional.empty(), Optional.empty(), replacementRemap, false);
        }

        private DraftState withPreview(
                ConfigurationPreview replacement,
                PhaseSixConfigurationCandidate replacementCandidate) {
            return new DraftState(draft, version, sourceSurface, rollbackSource, requiredKind,
                    Optional.of(replacement),
                    Optional.of(replacementCandidate), remapPlan, false);
        }

        private DraftState claimForApply() {
            if (applying) {
                throw new AdministrationException("config.draft.apply_in_progress",
                        "This exact draft is already being applied.",
                        "Wait for its current apply outcome; do not submit it concurrently.");
            }
            return new DraftState(draft, version, sourceSurface, rollbackSource, requiredKind, preview, candidate,
                    remapPlan, true);
        }
    }

    private record ConfigurationAcknowledgement(
            net.maddkraft.maddprestige.api.operation.Actor actor,
            UUID draftId,
            long draftVersion,
            ContentHash candidateHash,
            Optional<ConfigRevisionId> baseRevision,
            ConfigurationApplyKind kind,
            ContentHash findingSeal,
            Set<String> acknowledgedCodes,
            Instant expiresAt) {
        private ConfigurationAcknowledgement {
            actor = Objects.requireNonNull(actor, "actor");
            draftId = Objects.requireNonNull(draftId, "draft ID");
            candidateHash = Objects.requireNonNull(candidateHash, "candidate hash");
            baseRevision = Objects.requireNonNull(baseRevision, "base revision");
            kind = Objects.requireNonNull(kind, "kind");
            findingSeal = Objects.requireNonNull(findingSeal, "finding seal");
            acknowledgedCodes = Set.copyOf(Objects.requireNonNull(acknowledgedCodes, "acknowledged codes"));
            expiresAt = Objects.requireNonNull(expiresAt, "expiry");
            if (draftVersion < 1 || acknowledgedCodes.isEmpty()) {
                throw new IllegalArgumentException("Acknowledgement authority requires exact draft findings");
            }
        }
    }
}
