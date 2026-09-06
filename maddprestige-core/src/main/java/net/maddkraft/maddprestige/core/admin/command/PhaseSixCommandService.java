package net.maddkraft.maddprestige.core.admin.command;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.CostId;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.id.RewardId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.validation.ValidationSeverity;
import net.maddkraft.maddprestige.core.admin.AdministrationException;
import net.maddkraft.maddprestige.core.admin.ManualPrestigeAdministrationService;
import net.maddkraft.maddprestige.core.admin.OperationConfirmationService;
import net.maddkraft.maddprestige.core.admin.OperationPreview;
import net.maddkraft.maddprestige.core.admin.OperationPreviewService;
import net.maddkraft.maddprestige.core.admin.PermissionSubject;
import net.maddkraft.maddprestige.core.admin.PhaseSixPermissions;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationAdministrationService;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationIntrospectionService;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationPreview;
import net.maddkraft.maddprestige.core.admin.config.StructuredConfigurationValue;
import net.maddkraft.maddprestige.core.admin.diagnostic.DoctorService;
import net.maddkraft.maddprestige.core.admin.diagnostic.DiagnosticSeverity;
import net.maddkraft.maddprestige.core.admin.diagnostic.WhyReport;
import net.maddkraft.maddprestige.core.admin.diagnostic.WhyService;
import net.maddkraft.maddprestige.core.admin.player.PlayerProgressViewService;
import net.maddkraft.maddprestige.core.admin.presentation.MessageReference;
import net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation;
import net.maddkraft.maddprestige.core.admin.setup.SetupCost;
import net.maddkraft.maddprestige.core.admin.setup.SetupPrestige;
import net.maddkraft.maddprestige.core.admin.setup.SetupRequirement;
import net.maddkraft.maddprestige.core.admin.setup.SetupReward;
import net.maddkraft.maddprestige.core.admin.setup.SetupStage;
import net.maddkraft.maddprestige.core.admin.setup.SetupWizardService;
import net.maddkraft.maddprestige.core.admin.ui.GuiSessionService;
import net.maddkraft.maddprestige.core.admin.ui.PlayerGuiService;
import net.maddkraft.maddprestige.core.admin.ui.StaffGuiService;
import net.maddkraft.maddprestige.core.scaling.SegmentScalingMode;
import net.maddkraft.maddprestige.core.scaling.SegmentTransition;

/**
 * Bounded, platform-neutral V2 command surface. Callers pass tokenized arguments; synchronous history and
 * configuration inspection is dispatched on the supplied administration worker.
 */
public final class PhaseSixCommandService {
    private final ContextualHelpService help;
    private final ConfigurationIntrospectionService introspection;
    private final ConfigurationAdministrationService configuration;
    private final DoctorService doctor;
    private final WhyService why;
    private final OperationPreviewService previews;
    private final OperationConfirmationService confirmations;
    private final PlayerProgressViewService playerViews;
    private final SetupWizardService setup;
    private final ManualPrestigeAdministrationService prestigeAdministration;
    private final GuiSessionService gui;
    private final Optional<PlayerGuiService> playerGui;
    private final Optional<StaffGuiService> staffGui;
    private final Optional<StaffHistoryCommandService> staffHistory;
    private final Supplier<Optional<ConfigRevisionId>> activeRevision;
    private final Executor worker;

    public PhaseSixCommandService(
            ContextualHelpService help,
            ConfigurationIntrospectionService introspection,
            ConfigurationAdministrationService configuration,
            DoctorService doctor,
            WhyService why,
            OperationPreviewService previews,
            OperationConfirmationService confirmations,
            PlayerProgressViewService playerViews,
            SetupWizardService setup,
            ManualPrestigeAdministrationService prestigeAdministration,
            GuiSessionService gui,
            Supplier<Optional<ConfigRevisionId>> activeRevision,
            Executor worker) {
        this(help, introspection, configuration, doctor, why, previews, confirmations, playerViews, setup,
                prestigeAdministration, gui, null, null, activeRevision, worker);
    }

    public PhaseSixCommandService(
            ContextualHelpService help,
            ConfigurationIntrospectionService introspection,
            ConfigurationAdministrationService configuration,
            DoctorService doctor,
            WhyService why,
            OperationPreviewService previews,
            OperationConfirmationService confirmations,
            PlayerProgressViewService playerViews,
            SetupWizardService setup,
            ManualPrestigeAdministrationService prestigeAdministration,
            GuiSessionService gui,
            PlayerGuiService playerGui,
            Supplier<Optional<ConfigRevisionId>> activeRevision,
            Executor worker) {
        this(help, introspection, configuration, doctor, why, previews, confirmations, playerViews, setup,
                prestigeAdministration, gui, playerGui, null, activeRevision, worker);
    }

    public PhaseSixCommandService(
            ContextualHelpService help,
            ConfigurationIntrospectionService introspection,
            ConfigurationAdministrationService configuration,
            DoctorService doctor,
            WhyService why,
            OperationPreviewService previews,
            OperationConfirmationService confirmations,
            PlayerProgressViewService playerViews,
            SetupWizardService setup,
            ManualPrestigeAdministrationService prestigeAdministration,
            GuiSessionService gui,
            PlayerGuiService playerGui,
            StaffGuiService staffGui,
            Supplier<Optional<ConfigRevisionId>> activeRevision,
            Executor worker) {
        this(help, introspection, configuration, doctor, why, previews, confirmations, playerViews, setup,
                prestigeAdministration, gui, playerGui, staffGui, null, activeRevision, worker);
    }

    public PhaseSixCommandService(
            ContextualHelpService help,
            ConfigurationIntrospectionService introspection,
            ConfigurationAdministrationService configuration,
            DoctorService doctor,
            WhyService why,
            OperationPreviewService previews,
            OperationConfirmationService confirmations,
            PlayerProgressViewService playerViews,
            SetupWizardService setup,
            ManualPrestigeAdministrationService prestigeAdministration,
            GuiSessionService gui,
            PlayerGuiService playerGui,
            StaffGuiService staffGui,
            StaffHistoryCommandService staffHistory,
            Supplier<Optional<ConfigRevisionId>> activeRevision,
            Executor worker) {
        this.help = Objects.requireNonNull(help, "help");
        this.introspection = Objects.requireNonNull(introspection, "introspection");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.doctor = Objects.requireNonNull(doctor, "doctor");
        this.why = Objects.requireNonNull(why, "why");
        this.previews = Objects.requireNonNull(previews, "previews");
        this.confirmations = Objects.requireNonNull(confirmations, "confirmations");
        this.playerViews = Objects.requireNonNull(playerViews, "player views");
        this.setup = Objects.requireNonNull(setup, "setup");
        this.prestigeAdministration = Objects.requireNonNull(prestigeAdministration, "Prestige administration");
        this.gui = Objects.requireNonNull(gui, "GUI");
        this.playerGui = Optional.ofNullable(playerGui);
        this.staffGui = Optional.ofNullable(staffGui);
        this.staffHistory = Optional.ofNullable(staffHistory);
        this.activeRevision = Objects.requireNonNull(activeRevision, "active revision");
        this.worker = Objects.requireNonNull(worker, "worker");
    }

    public CompletionStage<CommandResponse> execute(CommandInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation");
        return CompletableFuture.supplyAsync(() -> dispatch(invocation), worker)
                .thenCompose(Function.identity()).handle((response, failure) -> failure == null
                        ? response : failure(failure));
    }

    private CompletionStage<CommandResponse> dispatch(CommandInvocation invocation) {
        List<String> arguments = invocation.arguments();
        if (arguments.isEmpty()) {
            return gui(invocation.subject());
        }
        PermissionSubject subject = invocation.subject();
        return switch (arguments.getFirst().toLowerCase(java.util.Locale.ROOT)) {
            case "help" -> help(subject, arguments);
            case "status" -> status(subject);
            case "rankup" -> throw rankUpCompatibilityOnly();
            case "prestige" -> prepare(subject, self(subject), true);
            case "confirm" -> confirm(subject, arguments);
            case "simulate" -> simulate(subject, arguments);
            case "why" -> why(subject, arguments);
            case "player" -> player(subject, arguments);
            case "gui" -> gui(subject);
            case "admin" -> admin(subject, arguments);
            case "history" -> history(subject, arguments);
            case "config" -> config(subject, arguments);
            case "setup" -> setup(subject, arguments);
            case "doctor" -> doctor(subject, arguments);
            case "staff" -> staff(subject, arguments);
            default -> completed(CommandResponse.failure("command.unknown",
                    m("command.unknown", "value", arguments.getFirst())));
        };
    }

    private CompletionStage<CommandResponse> help(PermissionSubject subject, List<String> arguments) {
        String topic = arguments.size() > 1 ? arguments.get(1) : "overview";
        List<MessageReference> lines = help.help(subject, topic);
        return completed(CommandResponse.success("help", lines.isEmpty()
                ? List.of(m("command.help.no_match", "topic", topic), m("command.help.suggestions"))
                : lines));
    }

    private CompletionStage<CommandResponse> status(PermissionSubject subject) {
        subject.require(PhaseSixPermissions.USE);
        String revision = activeRevision.get().map(ConfigRevisionId::value).orElse("inactive");
        return completed(CommandResponse.success("status", List.of(m("command.status.configuration",
                "revision", revision), m("command.status.guidance"))));
    }

    private CompletionStage<CommandResponse> prepare(PermissionSubject subject, UUID playerId, boolean prestige) {
        return (prestige ? confirmations.preparePrestige(subject, playerId)
                : confirmations.prepareRankUp(subject, playerId)).thenApply(value -> {
                    ArrayList<MessageReference> lines = new ArrayList<>(SemanticPresentation.preview(
                            "command.preview.summary", value.preview(), false));
                    lines.add(m("command.preview.confirmation_controls",
                            "confirmation", value.confirmationId()));
                    return CommandResponse.success(prestige ? "prestige.preview" : "rankup.preview", lines);
                });
    }

    private CompletionStage<CommandResponse> confirm(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() < 1 || arguments.size() > 2) {
            throw usage("confirm [confirmation-id]");
        }
        CompletionStage<net.maddkraft.maddprestige.core.admin.OperationExecutionResult> confirmation =
                arguments.size() == 1 ? confirmations.confirmOnly(subject)
                        : confirmations.confirm(subject, uuid(arguments.get(1), "confirmation ID"));
        return confirmation
                .thenApply(result -> CommandResponse.success("operation.executed", List.of(
                        m("command.operation.executed", "operation", result.operationId(),
                                "status", result.status()))));
    }

    private CompletionStage<CommandResponse> simulate(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() < 2 || arguments.size() > 4) {
            throw usage("simulate prestige [player-uuid] [details]");
        }
        boolean details = arguments.getLast().equalsIgnoreCase("details");
        int targetIndex = details ? arguments.size() - 2 : arguments.size() - 1;
        if (!details && arguments.size() == 4) {
            throw usage("simulate prestige [player-uuid] [details]");
        }
        UUID target = targetIndex == 2 ? uuid(arguments.get(2), "player UUID") : self(subject);
        CompletionStage<OperationPreview> stage = switch (arguments.get(1).toLowerCase(java.util.Locale.ROOT)) {
            case "rankup" -> throw rankUpCompatibilityOnly();
            case "prestige" -> previews.simulatePrestige(subject, target);
            default -> throw usage("simulate prestige [player-uuid] [details]");
        };
        return stage.thenApply(value -> CommandResponse.success("simulation",
                SemanticPresentation.preview("command.simulation.summary", value, details)));
    }

    private CompletionStage<CommandResponse> why(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() < 2 || arguments.size() > 4) {
            throw usage("why prestige [player-uuid] [details]");
        }
        boolean details = arguments.getLast().equalsIgnoreCase("details");
        int targetIndex = details ? arguments.size() - 2 : arguments.size() - 1;
        if (!details && arguments.size() == 4) {
            throw usage("why prestige [player-uuid] [details]");
        }
        UUID target = targetIndex == 2 ? uuid(arguments.get(2), "player UUID") : self(subject);
        CompletionStage<WhyReport> stage = switch (arguments.get(1).toLowerCase(java.util.Locale.ROOT)) {
            case "rankup" -> throw rankUpCompatibilityOnly();
            case "prestige" -> why.prestige(subject, target);
            default -> throw usage("why prestige [player-uuid] [details]");
        };
        return stage.thenApply(value -> CommandResponse.success("why", SemanticPresentation.why(value, details)));
    }

    private CompletionStage<CommandResponse> player(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() > 2) {
            throw usage("player [player-uuid]");
        }
        UUID target = arguments.size() == 2 ? uuid(arguments.get(1), "player UUID") : self(subject);
        return playerViews.view(subject, target).thenApply(view -> {
            return CommandResponse.success("player.progress", SemanticPresentation.player(view.prestige()));
        });
    }

    private CompletionStage<CommandResponse> gui(PermissionSubject subject) {
        UUID playerId = self(subject);
        CompletionStage<net.maddkraft.maddprestige.core.admin.ui.GuiSessionView> view = playerGui.isPresent()
                ? playerGui.orElseThrow().open(subject, playerId)
                : CompletableFuture.completedFuture(gui.openPlayer(subject, playerId));
        return view.thenApply(opened -> CommandResponse.gui("gui.open", List.of(), opened));
    }

    private CompletionStage<CommandResponse> admin(PermissionSubject subject, List<String> arguments) {
        net.maddkraft.maddprestige.core.admin.ui.StaffGuiService service = staffGui
                .orElseThrow(() -> new AdministrationException("gui.staff.unavailable",
                        "The Staff GUI is unavailable in this runtime.",
                        "Use the read-only command surfaces or try again after restart."));
        net.maddkraft.maddprestige.core.admin.ui.GuiSessionView view;
        if (arguments.size() == 1) {
            view = service.open(subject);
        } else if (arguments.size() == 3 && arguments.get(1).equalsIgnoreCase("find")) {
            view = service.findPlayers(subject, arguments.get(2));
        } else {
            throw usage("admin [find <player>]");
        }
        return completed(CommandResponse.gui("gui.open", List.of(), view));
    }

    private CompletionStage<CommandResponse> history(PermissionSubject subject, List<String> arguments) {
        return staffHistory.map(service -> service.execute(subject, arguments)).orElseGet(() -> completed(
                CommandResponse.failure("gui.staff.unavailable", List.of(
                        m("command.error.administration.gui_staff_unavailable.summary",
                                "code", "gui.staff.unavailable"),
                        m("command.error.administration.gui_staff_unavailable.remediation")))));
    }

    private CompletionStage<CommandResponse> config(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() < 2) {
            throw usage("config <get|list|search|explain|draft|set|add|remove|segment-add|segment-edit|"
                    + "segment-remove|validate|diff|acknowledge|confirm|"
                    + "cancel|history|rollback|apply> ...");
        }
        return switch (arguments.get(1).toLowerCase(java.util.Locale.ROOT)) {
            case "get" -> configGet(subject, arguments);
            case "explain" -> configExplain(subject, arguments);
            case "search" -> configSearch(subject, arguments);
            case "list" -> configList(subject, arguments);
            case "draft" -> configDraft(subject, arguments);
            case "set" -> configSet(subject, arguments);
            case "add" -> configAdd(subject, arguments);
            case "remove" -> configRemove(subject, arguments);
            case "segment-add" -> configSegmentAdd(subject, arguments);
            case "segment-edit" -> configSegmentEdit(subject, arguments);
            case "segment-remove" -> configSegmentRemove(subject, arguments);
            case "remap", "unmap" -> throw retiredStageSurface();
            case "validate" -> configPreview(subject, arguments, false);
            case "diff" -> configPreview(subject, arguments, true);
            case "acknowledge" -> configAcknowledge(subject, arguments);
            case "confirm" -> configConfirm(subject, arguments);
            case "cancel" -> configCancel(subject, arguments);
            case "history" -> configHistory(subject, arguments);
            case "rollback" -> configRollback(subject, arguments);
            case "apply" -> configApply(subject, arguments, false);
            case "rollback-apply" -> configApply(subject, arguments, true);
            default -> throw usage("config <get|list|search|explain|draft|set|add|remove|segment-add|segment-edit|"
                    + "segment-remove|validate|diff|"
                    + "acknowledge|"
                    + "confirm|cancel|history|rollback|apply> ...");
        };
    }

    private CompletionStage<CommandResponse> configExplain(PermissionSubject subject, List<String> arguments) {
        requireSize(arguments, 3, "config explain <canonical-path>");
        var value = introspection.explain(subject, arguments.get(2));
        return completed(CommandResponse.success("config.explain", List.of(
                effectiveValue(value),
                m("command.config.provenance", "source", provenance(value),
                        "configured", value.currentValue().orElse("NOT_SET"),
                        "default", value.defaultValue().orElse("NONE")),
                m("command.config.metadata", "type", value.type(), "status", value.reloadBehavior(),
                        "risk", value.risk()),
                SemanticPresentation.configurationDescription(value),
                m("command.config.allowed", "allowed", value.allowedValues().isEmpty()
                        ? "SCHEMA_OR_PROVIDER_DEFINED" : String.join(", ", value.allowedValues())))));
    }

    private CompletionStage<CommandResponse> configGet(PermissionSubject subject, List<String> arguments) {
        requireSize(arguments, 3, "config get <canonical-path>");
        var value = introspection.explain(subject, arguments.get(2));
        return completed(CommandResponse.success("config.get", List.of(effectiveValue(value))));
    }

    private CompletionStage<CommandResponse> configSearch(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() < 3) {
            throw usage("config search <text>");
        }
        String query = join(arguments, 2);
        List<MessageReference> lines = introspection.search(subject, query, 25).stream().map(value ->
                m("command.config.search_result", "path", value.canonicalPath(), "type", value.type(),
                        "value", value.currentValue())).toList();
        return completed(CommandResponse.success("config.search", lines.isEmpty()
                ? List.of(m("command.config.search_empty", "value", query)) : lines));
    }

    private CompletionStage<CommandResponse> configList(PermissionSubject subject, List<String> arguments) {
        requireSize(arguments, 4, "config list <draft-id> <canonical-path>");
        List<String> values = configuration.listValues(subject, uuid(arguments.get(2), "draft ID"),
                arguments.get(3));
        return completed(CommandResponse.success("config.list", values.isEmpty()
                ? List.of(m("command.config.list_empty"))
                : values.stream().map(value -> m("command.config.list_value", "value", value)).toList()));
    }

    private CompletionStage<CommandResponse> configDraft(PermissionSubject subject, List<String> arguments) {
        requireSize(arguments, 2, "config draft");
        UUID draft = configuration.beginDraft(subject, "command");
        return completed(CommandResponse.success("config.draft.created", List.of(
                m("command.config.draft_created", "draft", draft),
                m("command.config.draft_controls", "draft", draft),
                m("command.config.production_unchanged"))));
    }

    private CompletionStage<CommandResponse> configSet(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() < 5) {
            throw usage("config set <draft-id> <canonical-path> <value>");
        }
        UUID draft = uuid(arguments.get(2), "draft ID");
        String path = arguments.get(3);
        configuration.editScalar(subject, draft, path, join(arguments, 4));
        return completed(CommandResponse.success("config.draft.edited", List.of(
                m("command.config.draft_edited", "draft", draft, "path", path),
                m("command.config.production_unchanged"))));
    }

    private CompletionStage<CommandResponse> configAdd(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() < 5) {
            throw usage("config add <draft-id> <canonical-path> <value>");
        }
        UUID draft = uuid(arguments.get(2), "draft ID");
        String path = arguments.get(3);
        if (path.equals("progression.stages")) {
            throw retiredStageSurface();
        } else {
            requireSize(arguments, 5, "config add <draft-id> <list-path> <value>");
            configuration.addListValue(subject, draft, path, arguments.get(4));
        }
        return completed(CommandResponse.success("config.draft.structure_added", List.of(
                m("command.config.structure_added", "draft", draft),
                m("command.config.production_unchanged"))));
    }

    private CompletionStage<CommandResponse> configRemove(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() < 4 || arguments.size() > 5) {
            throw usage("config remove <draft-id> <canonical-path> [value-or-replacement-stage]");
        }
        UUID draft = uuid(arguments.get(2), "draft ID");
        String path = arguments.get(3);
        if (path.matches("progression\\.stages\\.[a-z][a-z0-9_-]{0,63}")) {
            throw retiredStageSurface();
        } else {
            requireSize(arguments, 5, "config remove <draft-id> <list-path> <value>");
            configuration.removeListValue(subject, draft, path, arguments.get(4));
        }
        return completed(CommandResponse.success("config.draft.structure_removed", List.of(
                m("command.config.structure_removed", "draft", draft),
                m("command.config.preview_guidance"))));
    }

    private CompletionStage<CommandResponse> configSegmentAdd(
            PermissionSubject subject,
            List<String> arguments) {
        if (arguments.size() < 10 || arguments.size() > 11) {
            throw usage("config segment-add <draft-id> <scaling-path> <start> <end|unlimited> "
                    + "<FLAT|LINEAR|EXPONENTIAL|MANUAL> <CONTINUE|EXPLICIT_BASE> <base> <rate> "
                    + "[level=value,...]");
        }
        UUID draft = uuid(arguments.get(2), "draft ID");
        String path = scalingSegmentsPath(arguments.get(3));
        configuration.addStructuredObject(subject, draft, path, Optional.empty(),
                scalingSegment(arguments, 4, 10));
        return completed(CommandResponse.success("config.draft.structure_added", List.of(
                m("command.config.structure_added", "draft", draft),
                m("command.config.production_unchanged"))));
    }

    private CompletionStage<CommandResponse> configSegmentEdit(
            PermissionSubject subject,
            List<String> arguments) {
        if (arguments.size() < 11 || arguments.size() > 12) {
            throw usage("config segment-edit <draft-id> <scaling-path> <index> <start> <end|unlimited> "
                    + "<FLAT|LINEAR|EXPONENTIAL|MANUAL> <CONTINUE|EXPLICIT_BASE> <base> <rate> "
                    + "[level=value,...]");
        }
        UUID draft = uuid(arguments.get(2), "draft ID");
        String path = scalingSegmentsPath(arguments.get(3));
        String selector = Long.toString(nonNegativeLong(arguments.get(4), "segment index"));
        configuration.editStructuredObject(subject, draft, path, selector,
                scalingSegment(arguments, 5, 11));
        return completed(CommandResponse.success("config.draft.edited", List.of(
                m("command.config.draft_edited", "draft", draft, "path", path + "." + selector),
                m("command.config.production_unchanged"))));
    }

    private CompletionStage<CommandResponse> configSegmentRemove(
            PermissionSubject subject,
            List<String> arguments) {
        requireSize(arguments, 5, "config segment-remove <draft-id> <scaling-path> <index>");
        UUID draft = uuid(arguments.get(2), "draft ID");
        String path = scalingSegmentsPath(arguments.get(3));
        String selector = Long.toString(nonNegativeLong(arguments.get(4), "segment index"));
        configuration.removeStructuredObject(subject, draft, path, selector);
        return completed(CommandResponse.success("config.draft.structure_removed", List.of(
                m("command.config.structure_removed", "draft", draft),
                m("command.config.preview_guidance"))));
    }

    private CompletionStage<CommandResponse> configPreview(
            PermissionSubject subject,
            List<String> arguments,
            boolean details) {
        requireSize(arguments, 3, "config validate <draft-id>");
        return configuration.preview(subject, uuid(arguments.get(2), "draft ID"))
                .thenApply(preview -> render(preview, details));
    }

    private CompletionStage<CommandResponse> configRemap(PermissionSubject subject, List<String> arguments) {
        requireSize(arguments, 5, "config remap <draft-id> <missing-stage> <replacement-stage>");
        UUID draft = uuid(arguments.get(2), "draft ID");
        configuration.selectStageRemap(subject, draft, new StageId(arguments.get(3)),
                new StageId(arguments.get(4)));
        return completed(CommandResponse.success("config.draft.remap_selected", List.of(
                m("command.config.remap_selected", "draft", draft, "stage", arguments.get(3),
                        "target", arguments.get(4)), m("command.config.remap_preview_guidance"))));
    }

    private CompletionStage<CommandResponse> configUnmap(PermissionSubject subject, List<String> arguments) {
        requireSize(arguments, 4, "config unmap <draft-id> <missing-stage>");
        UUID draft = uuid(arguments.get(2), "draft ID");
        configuration.removeStageRemap(subject, draft, new StageId(arguments.get(3)));
        return completed(CommandResponse.success("config.draft.remap_removed", List.of(
                m("command.config.remap_removed", "draft", draft, "stage", arguments.get(3)),
                m("command.config.remap_removed_guidance"))));
    }

    private CompletionStage<CommandResponse> configAcknowledge(
            PermissionSubject subject,
            List<String> arguments) {
        requireSize(arguments, 3, "config acknowledge <draft-id>");
        var prepared = configuration.prepareAcknowledgement(subject, uuid(arguments.get(2), "draft ID"));
        ArrayList<MessageReference> lines = new ArrayList<>();
        prepared.findings().forEach(finding -> lines.addAll(SemanticPresentation.validationFinding(finding)));
        lines.add(m("command.acknowledgement.id", "acknowledgement", prepared.acknowledgementId()));
        lines.add(m("command.acknowledgement.expires", "expires", prepared.expiresAt()));
        return completed(CommandResponse.success("config.acknowledgement.prepared", lines));
    }

    private CompletionStage<CommandResponse> configConfirm(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() < 4) {
            throw usage("config confirm <acknowledgement-id> <reason>");
        }
        return configuration.confirmAcknowledgement(subject, uuid(arguments.get(2), "acknowledgement ID"),
                join(arguments, 3)).thenApply(value -> CommandResponse.success("config.applied", List.of(
                        m("command.config.acknowledged_applied", "revision", value.id().value(),
                                "status", value.status()))));
    }

    private CompletionStage<CommandResponse> configCancel(PermissionSubject subject, List<String> arguments) {
        requireSize(arguments, 3, "config cancel <draft-id>");
        UUID draft = uuid(arguments.get(2), "draft ID");
        configuration.discardDraft(subject, draft);
        return completed(CommandResponse.success("config.draft.cancelled", List.of(
                m("command.config.draft_cancelled", "draft", draft))));
    }

    private CompletionStage<CommandResponse> configHistory(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() > 3) {
            throw usage("config history [limit]");
        }
        int limit = arguments.size() == 3 ? positiveInteger(arguments.get(2), "history limit") : 20;
        List<MessageReference> lines = configuration.history(subject, limit).stream().map(revision ->
                m("command.config.history_entry", "revision", revision.id().value(),
                        "status", revision.status(), "player", revision.actor().displayName())).toList();
        return completed(CommandResponse.success("config.history", lines.isEmpty()
                ? List.of(m("command.config.history_empty")) : lines));
    }

    private CompletionStage<CommandResponse> configRollback(PermissionSubject subject, List<String> arguments) {
        requireSize(arguments, 3, "config rollback <revision-id>");
        UUID draft = configuration.beginRollback(subject, new ConfigRevisionId(arguments.get(2)), "command");
        return configuration.preview(subject, draft).thenApply(preview -> {
            ArrayList<MessageReference> lines = new ArrayList<>(renderPreview(preview, true));
            lines.add(m("command.config.rollback_draft", "draft", draft));
            lines.add(m("command.config.rollback_history_safe"));
            return CommandResponse.success("config.rollback.preview", lines);
        });
    }

    private CompletionStage<CommandResponse> configApply(
            PermissionSubject subject,
            List<String> arguments,
            boolean rollback) {
        if (arguments.size() < 5) {
            throw usage("config " + (rollback ? "rollback-apply" : "apply")
                    + " <draft-id> <expected-revision|none> <reason>");
        }
        UUID draft = uuid(arguments.get(2), "draft ID");
        Optional<ConfigRevisionId> expected = arguments.get(3).equalsIgnoreCase("none")
                ? Optional.empty() : Optional.of(new ConfigRevisionId(arguments.get(3)));
        String reason = join(arguments, 4);
        var stage = rollback
                ? configuration.applyRollback(subject, draft, expected, Set.of(), reason)
                : configuration.applyDraft(subject, draft, expected, Set.of(), reason);
        return stage.thenApply(value -> CommandResponse.success("config.applied", List.of(
                m("command.config.applied", "revision", value.id().value(), "status", value.status()))));
    }

    private CompletionStage<CommandResponse> setup(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() < 2) {
            throw usage("setup <discover|start|provider|requirement|cost|reward|prestige|"
                    + "preview|acknowledge|confirm|apply|cancel> ...");
        }
        return switch (arguments.get(1).toLowerCase(java.util.Locale.ROOT)) {
            case "discover" -> setupDiscover(subject, arguments);
            case "start" -> setupStart(subject, arguments);
            case "provider" -> setupProvider(subject, arguments);
            case "stage", "baseline", "playtime" -> throw retiredStageSurface();
            case "requirement" -> setupRequirement(subject, arguments);
            case "cost" -> setupCost(subject, arguments);
            case "reward" -> setupReward(subject, arguments);
            case "prestige" -> setupPrestige(subject, arguments);
            case "preview" -> setupPreview(subject, arguments);
            case "acknowledge" -> setupAcknowledge(subject, arguments);
            case "confirm" -> setupConfirm(subject, arguments);
            case "apply" -> setupApply(subject, arguments);
            case "cancel" -> setupCancel(subject, arguments);
            default -> throw usage("setup <discover|start|provider|requirement|cost|reward|"
                    + "prestige|preview|acknowledge|confirm|apply|cancel> ...");
        };
    }

    private CompletionStage<CommandResponse> setupDiscover(PermissionSubject subject, List<String> arguments) {
        requireSize(arguments, 2, "setup discover");
        var discovery = setup.discover(subject);
        ArrayList<MessageReference> lines = new ArrayList<>();
        lines.add(m("command.setup.discovery_active", "active", discovery.activeConfigurationPresent()));
        discovery.providers().forEach(provider -> lines.add(m("command.setup.discovery_provider",
                "provider", provider.providerId().value(), "active", provider.active(),
                "healthy", provider.healthy(), "metrics", provider.metricIds())));
        lines.add(m("command.setup.external_group_policy"));
        return completed(CommandResponse.success("setup.discovery", lines));
    }

    private CompletionStage<CommandResponse> setupStart(PermissionSubject subject, List<String> arguments) {
        requireSize(arguments, 2, "setup start");
        return completed(CommandResponse.success("setup.started", List.of(
                m("command.setup.started", "id", setup.start(subject)), m("command.setup.started_guidance"))));
    }

    private CompletionStage<CommandResponse> setupProvider(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() != 3 && arguments.size() != 4) {
            throw usage("setup provider [session-id] <provider-id|internal>");
        }
        boolean explicit = arguments.size() == 4;
        String provider = arguments.get(explicit ? 3 : 2);
        setup.selectRankProvider(subject, setupSession(subject, arguments, explicit),
                provider.equalsIgnoreCase("internal") ? Optional.empty() : Optional.of(new ProviderId(provider)));
        return completed(CommandResponse.success("setup.provider.selected", List.of(
                m("command.setup.provider_selected"))));
    }

    private CompletionStage<CommandResponse> setupStage(PermissionSubject subject, List<String> arguments) {
        boolean explicit = arguments.size() > 2 && looksLikeUuid(arguments.get(2));
        int first = explicit ? 3 : 2;
        if (arguments.size() < first + 2 || arguments.size() > first + 3) {
            throw usage("setup stage [session-id] <stage-id> <display-name> [existing-group]");
        }
        setup.addStage(subject, setupSession(subject, arguments, explicit), new SetupStage(
                new StageId(arguments.get(first)), arguments.get(first + 1),
                arguments.size() == first + 3 ? Optional.of(arguments.get(first + 2)) : Optional.empty()));
        return completed(CommandResponse.success("setup.stage.added", List.of(m("command.setup.stage_added"))));
    }

    private CompletionStage<CommandResponse> setupBaseline(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() != 3 && arguments.size() != 4) {
            throw usage("setup baseline [session-id] <stage-id>");
        }
        boolean explicit = arguments.size() == 4;
        setup.selectBaseline(subject, setupSession(subject, arguments, explicit),
                new StageId(arguments.get(explicit ? 3 : 2)));
        return completed(CommandResponse.success("setup.baseline.selected",
                List.of(m("command.setup.baseline_selected"))));
    }

    private CompletionStage<CommandResponse> setupPlaytime(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() != 4 && arguments.size() != 5) {
            throw usage("setup playtime [session-id] <target-stage> <duration>");
        }
        boolean explicit = arguments.size() == 5;
        int first = explicit ? 3 : 2;
        setup.configurePlaytimeRequirement(subject, setupSession(subject, arguments, explicit),
                new StageId(arguments.get(first)), arguments.get(first + 1));
        return completed(CommandResponse.success("setup.requirement.configured",
                List.of(m("command.setup.playtime_configured", "stage", arguments.get(first),
                        "target", arguments.get(first + 1)))));
    }

    private CompletionStage<CommandResponse> setupRequirement(
            PermissionSubject subject,
            List<String> arguments) {
        boolean explicit = arguments.size() > 2 && looksLikeUuid(arguments.get(2));
        int first = explicit ? 3 : 2;
        int fields = arguments.size() - first;
        if (fields != 7) {
            throw usage("setup requirement [session] <id> <provider> <metric> <operator> "
                    + "<target> <scope> <completion>");
        }
        UUID sessionId = setupSession(subject, arguments, explicit);
        int requirement = first;
        SetupRequirement value = new SetupRequirement(new RequirementId(arguments.get(requirement)),
                new ProviderId(arguments.get(requirement + 1)), new MetricId(arguments.get(requirement + 2)),
                arguments.get(requirement + 3), arguments.get(requirement + 4), arguments.get(requirement + 5),
                arguments.get(requirement + 6));
        setup.configureRequirement(subject, sessionId, value);
        return completed(CommandResponse.success("setup.requirement.configured",
                List.of(m("command.setup.requirement_configured"))));
    }

    private CompletionStage<CommandResponse> setupCost(PermissionSubject subject, List<String> arguments) {
        requireSize(arguments, 9,
                "setup cost <session> <id> <provider> <type> <value-type> <amount> <display-name>");
        setup.configureCost(subject, uuid(arguments.get(2), "setup session"), new SetupCost(
                new CostId(arguments.get(3)), new ProviderId(arguments.get(4)), arguments.get(5), arguments.get(6),
                arguments.get(7), arguments.get(8)));
        return completed(CommandResponse.success("setup.cost.configured",
                List.of(m("command.setup.cost_configured"))));
    }

    private CompletionStage<CommandResponse> setupReward(PermissionSubject subject, List<String> arguments) {
        requireSize(arguments, 9,
                "setup reward <session> <id> <provider> <type> <value-type> <value> <display-name>");
        setup.configureReward(subject, uuid(arguments.get(2), "setup session"), new SetupReward(
                new RewardId(arguments.get(3)), new ProviderId(arguments.get(4)), arguments.get(5), arguments.get(6),
                arguments.get(7), arguments.get(8)));
        return completed(CommandResponse.success("setup.reward.configured",
                List.of(m("command.setup.reward_configured"))));
    }

    private CompletionStage<CommandResponse> setupPrestige(PermissionSubject subject, List<String> arguments) {
        boolean explicit = arguments.size() > 2 && looksLikeUuid(arguments.get(2));
        int mode = explicit ? 3 : 2;
        boolean disabled = arguments.size() == mode + 1 && arguments.get(mode).equalsIgnoreCase("disabled");
        boolean numericEnabled = arguments.size() == mode + 1 && arguments.get(mode).equalsIgnoreCase("enabled");
        if (!disabled && !numericEnabled) {
            throw usage("setup prestige [session] disabled | setup prestige [session] enabled");
        }
        UUID sessionId = setupSession(subject, arguments, explicit);
        if (disabled) {
            setup.configurePrestige(subject, sessionId, SetupPrestige.disabled());
        } else {
            setup.configurePrestige(subject, sessionId, SetupPrestige.numericEnabled());
        }
        return completed(CommandResponse.success("setup.prestige.configured",
                List.of(m("command.setup.prestige_configured"))));
    }

    private CompletionStage<CommandResponse> setupPreview(PermissionSubject subject, List<String> arguments) {
        boolean details = arguments.getLast().equalsIgnoreCase("details");
        int effectiveSize = details ? arguments.size() - 1 : arguments.size();
        if (effectiveSize != 2 && effectiveSize != 3) {
            throw usage("setup preview [session-id] [details]");
        }
        boolean explicitSession = effectiveSize == 3;
        return setup.preview(subject, setupSession(subject, arguments, explicitSession)).thenApply(value -> {
            ArrayList<MessageReference> lines = new ArrayList<>(renderPreview(value.configuration(), details));
            lines.addAll(value.playerExperience());
            return CommandResponse.success("setup.preview", lines);
        });
    }

    private CompletionStage<CommandResponse> setupApply(PermissionSubject subject, List<String> arguments) {
        boolean explicit = arguments.size() > 2 && looksLikeUuid(arguments.get(2));
        int reason = explicit ? 3 : 2;
        if (arguments.size() <= reason) {
            throw usage("setup apply [session-id] <reason>");
        }
        return setup.apply(subject, setupSession(subject, arguments, explicit), Set.of(), join(arguments, reason))
                .thenApply(value -> CommandResponse.success("setup.applied", List.of(
                        m("command.setup.applied", "revision", value.id().value()))));
    }

    private CompletionStage<CommandResponse> setupAcknowledge(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() != 2 && arguments.size() != 3) {
            throw usage("setup acknowledge [session-id]");
        }
        var prepared = setup.prepareAcknowledgement(subject,
                setupSession(subject, arguments, arguments.size() == 3));
        ArrayList<MessageReference> lines = new ArrayList<>();
        prepared.findings().forEach(finding -> lines.addAll(SemanticPresentation.validationFinding(finding)));
        lines.add(m("command.acknowledgement.id", "acknowledgement", prepared.acknowledgementId()));
        lines.add(m("command.setup.confirm_guidance", "expires", prepared.expiresAt()));
        return completed(CommandResponse.success("setup.acknowledgement.prepared", lines));
    }

    private CompletionStage<CommandResponse> setupConfirm(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() < 4) {
            throw usage("setup confirm <server-token> <reason>");
        }
        return setup.confirmAcknowledgement(subject, uuid(arguments.get(2), "setup acknowledgement"),
                join(arguments, 3)).thenApply(value -> CommandResponse.success("setup.applied", List.of(
                        m("command.setup.applied", "revision", value.id().value()))));
    }

    private CompletionStage<CommandResponse> setupCancel(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() != 2 && arguments.size() != 3) {
            throw usage("setup cancel [session-id]");
        }
        setup.cancel(subject, setupSession(subject, arguments, arguments.size() == 3));
        return completed(CommandResponse.success("setup.cancelled", List.of(m("command.setup.cancelled"))));
    }

    private UUID setupSession(PermissionSubject subject, List<String> arguments, boolean explicit) {
        return explicit ? uuid(arguments.get(2), "setup session") : setup.currentSession(subject);
    }

    private static boolean looksLikeUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private CompletionStage<CommandResponse> doctor(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() > 2 || arguments.size() == 2 && !arguments.get(1).equalsIgnoreCase("details")) {
            throw usage("doctor [details]");
        }
        boolean details = arguments.size() == 2;
        return doctor.inspect(subject).thenApply(report -> {
            ArrayList<MessageReference> lines = new ArrayList<>();
            lines.add(m("command.doctor.status", "status", report.status()));
            lines.add(m("command.doctor.summary", "blocked", count(report.findings(), DiagnosticSeverity.BLOCKED),
                    "warnings", count(report.findings(), DiagnosticSeverity.WARNING),
                    "deferred", count(report.findings(), DiagnosticSeverity.DEFERRED),
                    "healthy", count(report.findings(), DiagnosticSeverity.HEALTHY)));
            if (details) {
                report.findings().forEach(finding -> lines.addAll(SemanticPresentation.doctorFinding(finding)));
            } else {
                report.findings().stream().filter(finding -> finding.severity() == DiagnosticSeverity.BLOCKED
                        || finding.severity() == DiagnosticSeverity.WARNING)
                        .forEach(finding -> lines.add(SemanticPresentation.doctorFindingSummary(finding)));
            }
            return CommandResponse.success("doctor", lines);
        });
    }

    private CompletionStage<CommandResponse> staff(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() < 7 || !arguments.get(1).equalsIgnoreCase("prestige")
                || !arguments.get(2).equalsIgnoreCase("set")) {
            throw usage("staff prestige set <player> <expected-state-revision> <prestige> <reason>");
        }
        UUID playerId = uuid(arguments.get(3), "player UUID");
        long expected = nonNegativeLong(arguments.get(4), "expected state revision");
        long prestige = nonNegativeLong(arguments.get(5), "Prestige");
        return prestigeAdministration.set(subject, playerId, expected, prestige, "command",
                join(arguments, 6)).thenApply(state -> CommandResponse.success("staff.prestige.adjusted", List.of(
                        m("command.staff.prestige_adjusted", "player", playerId, "current", state.currentPrestige(),
                                "lifetime", state.lifetimePrestige(), "revision", state.stateRevision()))));
    }

    private static CommandResponse render(ConfigurationPreview preview, boolean details) {
        return CommandResponse.success("config.preview", renderPreview(preview, details));
    }

    private static List<MessageReference> renderPreview(ConfigurationPreview preview, boolean details) {
        ArrayList<MessageReference> lines = new ArrayList<>();
        lines.add(m("command.config.preview_summary", "draft", preview.draftId(), "status",
                preview.validation().hasErrors() ? "BLOCKED" : "VALID", "count",
                preview.changedDocuments().size(), "findings", preview.validation().findings().size()));
        if (details) {
            lines.add(m("command.config.preview_header", "draft", preview.draftId(), "base",
                    preview.baseRevision().map(ConfigRevisionId::value).orElse("NONE"),
                    "hash", preview.candidateHash().value()));
            lines.add(m("command.config.preview_version", "version", preview.draftVersion()));
            lines.add(m("command.config.preview_documents", "value", String.join(", ", preview.changedDocuments())));
            preview.validation().findings().forEach(finding ->
                    lines.addAll(SemanticPresentation.validationFinding(finding)));
        } else {
            preview.validation().findings().forEach(finding -> {
                lines.add(SemanticPresentation.validationFindingSummary(finding));
                if (finding.severity() == ValidationSeverity.ERROR) {
                    lines.addAll(SemanticPresentation.validationFinding(finding).subList(1, 2));
                }
            });
        }
        if (preview.stale()) {
            lines.add(m("command.config.preview_stale"));
        }
        if (details && !preview.stageImpact().semanticDiff().entries().isEmpty()) {
            preview.stageImpact().semanticDiff().entries().forEach(entry -> lines.add(m("command.config.preview_diff",
                    "kind", entry.kind(), "path", entry.path(), "current",
                    entry.redactedOldValue().orElse("ABSENT"), "value",
                    entry.redactedNewValue().orElse("ABSENT"))));
        }
        if (!preview.stageImpact().affectedPlayerReferences().isEmpty()) {
            lines.add(m("command.config.preview_affected", "count",
                    preview.stageImpact().affectedPlayerReferences().size()));
            lines.add(m("command.config.preview_remap", "hash",
                    preview.stageRemapSeal().map(value -> value.value()).orElse("MISSING")));
        }
        return List.copyOf(lines);
    }

    private static MessageReference effectiveValue(
            net.maddkraft.maddprestige.core.admin.config.ConfigurationExplanation value) {
        return m("command.config.effective", "path", value.canonicalPath(), "value",
                value.currentValue().or(() -> value.defaultValue()).orElse("NOT_SET"), "source", provenance(value));
    }

    private static String provenance(
            net.maddkraft.maddprestige.core.admin.config.ConfigurationExplanation value) {
        return value.currentValue().isPresent() ? "CONFIGURED"
                : value.defaultValue().isPresent() ? "INHERITED_DEFAULT" : "NOT_SET";
    }

    private static long count(
            List<net.maddkraft.maddprestige.core.admin.diagnostic.DiagnosticFinding> findings,
            DiagnosticSeverity severity) {
        return findings.stream().filter(finding -> finding.severity() == severity).count();
    }

    private static CommandResponse failure(Throwable failure) {
        Throwable cause = unwrap(failure);
        if (cause instanceof AdministrationException exception) {
            return CommandResponse.failure(exception.code(), SemanticPresentation.administration(exception));
        }
        if (cause instanceof CommandUsageException exception) {
            return CommandResponse.failure("command.invalid",
                    m("command.error.usage", "usage", "/maddprestige " + exception.usage()));
        }
        if (cause instanceof IllegalArgumentException) {
            return CommandResponse.failure("command.invalid", m("command.error.invalid"));
        }
        return CommandResponse.failure("command.failed", m("command.error.internal"));
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static UUID self(PermissionSubject subject) {
        return subject.actor().uuid().orElseThrow(() -> new AdministrationException(
                "command.player_required", "This command requires a player target.",
                "Supply a player UUID for staff inspection/simulation or run the self-service command in-game."));
    }

    private static UUID uuid(String value, String label) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid " + label + ": " + value);
        }
    }

    private static int positiveInteger(String value, String label) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid " + label + ": " + value);
        }
    }

    private static long positiveLong(String value, String label) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 1) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid " + label + ": " + value);
        }
    }

    private static BigDecimal configurationDecimal(String value, String label) {
        if (!value.matches("(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?")) {
            throw new IllegalArgumentException("Invalid " + label + ": " + value);
        }
        return new BigDecimal(value);
    }

    private static String scalingSegmentsPath(String scalingPath) {
        if (!scalingPath.matches("(?:requirements\\.requirements\\.[a-z][a-z0-9_-]{0,63}\\.scaling"
                + "|prestige\\.(?:cost|reward)-scaling\\.[a-z][a-z0-9_-]{0,63})")) {
            throw new IllegalArgumentException("Invalid scaling path: " + scalingPath);
        }
        return scalingPath + ".segments";
    }

    private static StructuredConfigurationValue scalingSegment(
            List<String> arguments,
            int offset,
            int overridesIndex) {
        long start = positiveLong(arguments.get(offset), "segment start Prestige");
        String end = arguments.get(offset + 1).toLowerCase(java.util.Locale.ROOT);
        if (!end.equals("unlimited")) {
            end = Long.toString(positiveLong(end, "segment end Prestige"));
        }
        if (!end.equals("unlimited") && Long.parseLong(end) < start) {
            throw new IllegalArgumentException("Segment end Prestige must not precede its start");
        }
        String mode = arguments.get(offset + 2).toUpperCase(java.util.Locale.ROOT);
        String transition = arguments.get(offset + 3).toUpperCase(java.util.Locale.ROOT);
        SegmentScalingMode.valueOf(mode);
        SegmentTransition.valueOf(transition);

        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        fields.put("start-prestige", start);
        fields.put("end-prestige", end);
        fields.put("mode", mode);
        fields.put("transition", transition);
        fields.put("base", configurationDecimal(arguments.get(offset + 4), "segment base"));
        fields.put("rate", configurationDecimal(arguments.get(offset + 5), "segment rate"));
        if (arguments.size() > overridesIndex && !arguments.get(overridesIndex).equals("-")) {
            fields.put("overrides", scalingOverrides(arguments.get(overridesIndex)));
        }
        return new StructuredConfigurationValue(fields);
    }

    private static Map<String, Object> scalingOverrides(String value) {
        LinkedHashMap<String, Object> overrides = new LinkedHashMap<>();
        for (String entry : value.split(",", -1)) {
            String[] parts = entry.split("=", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid MANUAL override: " + entry);
            }
            String level = Long.toString(positiveLong(parts[0], "MANUAL override Prestige"));
            Object prior = overrides.put(level,
                    configurationDecimal(parts[1], "MANUAL override value"));
            if (prior != null) {
                throw new IllegalArgumentException("Duplicate MANUAL override Prestige: " + level);
            }
        }
        return overrides;
    }

    private static long nonNegativeLong(String value, String label) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid " + label + ": " + value);
        }
    }

    private static void requireSize(List<String> arguments, int size, String usage) {
        if (arguments.size() != size) {
            throw usage(usage);
        }
    }

    private static IllegalArgumentException usage(String usage) {
        return new CommandUsageException(usage);
    }

    private static String join(List<String> arguments, int start) {
        return String.join(" ", arguments.subList(start, arguments.size()));
    }

    private static MessageReference m(String key, Object... arguments) {
        return MessageReference.of(key, arguments);
    }

    private static AdministrationException rankUpCompatibilityOnly() {
        return new AdministrationException("rankup.compatibility_only",
                "Rank-up is a compatibility-only surface and cannot be simulated or executed.",
                "Use the numeric Prestige operation; active progression is Prestige N to N + 1.");
    }

    private static AdministrationException retiredStageSurface() {
        return new AdministrationException("stage.compatibility_only",
                "Stage configuration is preserved only for compatibility and recovery evidence.",
                "Configure numeric Prestige requirements, costs, rewards, and scaling instead.");
    }

    private static CompletionStage<CommandResponse> completed(CommandResponse response) {
        return CompletableFuture.completedFuture(response);
    }

    private static final class CommandUsageException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private final String usage;

        private CommandUsageException(String usage) {
            super("Usage: /maddprestige " + usage);
            this.usage = usage;
        }

        private String usage() {
            return usage;
        }
    }
}
