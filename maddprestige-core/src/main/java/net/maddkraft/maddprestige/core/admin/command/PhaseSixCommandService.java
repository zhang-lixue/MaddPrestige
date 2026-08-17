package net.maddkraft.maddprestige.core.admin.command;

import java.util.ArrayList;
import java.util.List;
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
import net.maddkraft.maddprestige.core.admin.diagnostic.DoctorService;
import net.maddkraft.maddprestige.core.admin.diagnostic.WhyReport;
import net.maddkraft.maddprestige.core.admin.diagnostic.WhyService;
import net.maddkraft.maddprestige.core.admin.player.PlayerProgressViewService;
import net.maddkraft.maddprestige.core.admin.setup.SetupCost;
import net.maddkraft.maddprestige.core.admin.setup.SetupPrestige;
import net.maddkraft.maddprestige.core.admin.setup.SetupRequirement;
import net.maddkraft.maddprestige.core.admin.setup.SetupReward;
import net.maddkraft.maddprestige.core.admin.setup.SetupStage;
import net.maddkraft.maddprestige.core.admin.setup.SetupWizardService;
import net.maddkraft.maddprestige.core.admin.ui.GuiSessionService;

/**
 * Bounded, platform-neutral V2 command surface. Callers pass tokenized arguments; synchronous history and
 * configuration inspection is dispatched on the supplied administration worker.
 */
public final class PhaseSixCommandService {
    private static final String USAGE = "Use help, status, rankup, prestige, confirm, simulate, why, player, gui, "
            + "config, setup, doctor, or staff.";
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
            return completed(CommandResponse.failure("command.usage", "No subcommand was provided.", USAGE));
        }
        PermissionSubject subject = invocation.subject();
        return switch (arguments.getFirst().toLowerCase(java.util.Locale.ROOT)) {
            case "help" -> help(subject, arguments);
            case "status" -> status(subject);
            case "rankup" -> prepare(subject, self(subject), false);
            case "prestige" -> prepare(subject, self(subject), true);
            case "confirm" -> confirm(subject, arguments);
            case "simulate" -> simulate(subject, arguments);
            case "why" -> why(subject, arguments);
            case "player" -> player(subject, arguments);
            case "gui" -> gui(subject);
            case "config" -> config(subject, arguments);
            case "setup" -> setup(subject, arguments);
            case "doctor" -> doctor(subject);
            case "staff" -> staff(subject, arguments);
            default -> completed(CommandResponse.failure("command.unknown",
                    "Unknown MaddPrestige V2 subcommand: " + arguments.getFirst(), USAGE));
        };
    }

    private CompletionStage<CommandResponse> help(PermissionSubject subject, List<String> arguments) {
        String topic = arguments.size() > 1 ? arguments.get(1) : "stages";
        List<String> lines = help.help(subject, topic);
        return completed(CommandResponse.success("help", lines.isEmpty()
                ? List.of("No schema-owned help matched '" + topic + "'.", "Try measurement, stages, or providers.")
                : lines));
    }

    private CompletionStage<CommandResponse> status(PermissionSubject subject) {
        subject.require(PhaseSixPermissions.USE);
        String revision = activeRevision.get().map(ConfigRevisionId::value).orElse("inactive");
        return completed(CommandResponse.success("status", List.of("MaddPrestige V2 configuration: " + revision,
                "Use player for canonical progress or doctor for administrative diagnostics.")));
    }

    private CompletionStage<CommandResponse> prepare(PermissionSubject subject, UUID playerId, boolean prestige) {
        return (prestige ? confirmations.preparePrestige(subject, playerId)
                : confirmations.prepareRankUp(subject, playerId)).thenApply(value -> CommandResponse.success(
                        prestige ? "prestige.preview" : "rankup.preview",
                        List.of(render(value.preview()), "Confirmation: " + value.confirmationId(),
                                "Expires: " + value.expiresAt())));
    }

    private CompletionStage<CommandResponse> confirm(PermissionSubject subject, List<String> arguments) {
        requireSize(arguments, 2, "confirm <confirmation-id>");
        return confirmations.confirm(subject, uuid(arguments.get(1), "confirmation ID"))
                .thenApply(result -> CommandResponse.success("operation.executed", List.of(
                        "Operation " + result.operationId() + ": " + result.status(), result.detail())));
    }

    private CompletionStage<CommandResponse> simulate(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() < 2 || arguments.size() > 3) {
            throw usage("simulate <rankup|prestige> [player-uuid]");
        }
        UUID target = arguments.size() == 3 ? uuid(arguments.get(2), "player UUID") : self(subject);
        CompletionStage<OperationPreview> stage = switch (arguments.get(1).toLowerCase(java.util.Locale.ROOT)) {
            case "rankup" -> previews.simulateRankUp(subject, target);
            case "prestige" -> previews.simulatePrestige(subject, target);
            default -> throw usage("simulate <rankup|prestige> [player-uuid]");
        };
        return stage.thenApply(value -> CommandResponse.success("simulation", List.of(render(value))));
    }

    private CompletionStage<CommandResponse> why(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() < 2 || arguments.size() > 3) {
            throw usage("why <rankup|prestige> [player-uuid]");
        }
        UUID target = arguments.size() == 3 ? uuid(arguments.get(2), "player UUID") : self(subject);
        CompletionStage<WhyReport> stage = switch (arguments.get(1).toLowerCase(java.util.Locale.ROOT)) {
            case "rankup" -> why.rankUp(subject, target);
            case "prestige" -> why.prestige(subject, target);
            default -> throw usage("why <rankup|prestige> [player-uuid]");
        };
        return stage.thenApply(value -> CommandResponse.success("why", render(value)));
    }

    private CompletionStage<CommandResponse> player(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() > 2) {
            throw usage("player [player-uuid]");
        }
        UUID target = arguments.size() == 2 ? uuid(arguments.get(1), "player UUID") : self(subject);
        return playerViews.view(subject, target).thenApply(view -> CommandResponse.success("player.progress",
                List.of("Rank-up: " + render(view.rankUp()), "Prestige: " + render(view.prestige()))));
    }

    private CompletionStage<CommandResponse> gui(PermissionSubject subject) {
        self(subject);
        var view = subject.has(PhaseSixPermissions.ADMIN_GUI) ? gui.openStaff(subject)
                : gui.openPlayer(subject, self(subject));
        return completed(CommandResponse.gui("gui.open", List.of("GUI session: " + view.sessionId(),
                view.title(), "Server-owned actions: " + view.actions().size()), view));
    }

    private CompletionStage<CommandResponse> config(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() < 2) {
            throw usage("config <get|list|search|explain|draft|set|add|remove|remap|unmap|validate|diff|acknowledge|confirm|"
                    + "cancel|history|rollback|apply> ...");
        }
        return switch (arguments.get(1).toLowerCase(java.util.Locale.ROOT)) {
            case "get", "explain" -> configExplain(subject, arguments);
            case "search" -> configSearch(subject, arguments);
            case "list" -> configList(subject, arguments);
            case "draft" -> configDraft(subject, arguments);
            case "set" -> configSet(subject, arguments);
            case "add" -> configAdd(subject, arguments);
            case "remove" -> configRemove(subject, arguments);
            case "remap" -> configRemap(subject, arguments);
            case "unmap" -> configUnmap(subject, arguments);
            case "validate", "diff" -> configPreview(subject, arguments);
            case "acknowledge" -> configAcknowledge(subject, arguments);
            case "confirm" -> configConfirm(subject, arguments);
            case "cancel" -> configCancel(subject, arguments);
            case "history" -> configHistory(subject, arguments);
            case "rollback" -> configRollback(subject, arguments);
            case "apply" -> configApply(subject, arguments, false);
            case "rollback-apply" -> configApply(subject, arguments, true);
            default -> throw usage("config <get|list|search|explain|draft|set|add|remove|remap|unmap|validate|diff|"
                    + "acknowledge|"
                    + "confirm|cancel|history|rollback|apply> ...");
        };
    }

    private CompletionStage<CommandResponse> configExplain(PermissionSubject subject, List<String> arguments) {
        requireSize(arguments, 3, "config explain <canonical-path>");
        var value = introspection.explain(subject, arguments.get(2));
        return completed(CommandResponse.success("config.explain", List.of(
                value.canonicalPath() + " = " + value.currentValue().orElse("<not set>"),
                "Type: " + value.type() + "; reload: " + value.reloadBehavior() + "; risk: " + value.risk(),
                value.description(), "Allowed: " + (value.allowedValues().isEmpty()
                        ? "schema/provider-defined" : String.join(", ", value.allowedValues())))));
    }

    private CompletionStage<CommandResponse> configSearch(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() < 3) {
            throw usage("config search <text>");
        }
        String query = join(arguments, 2);
        List<String> lines = introspection.search(subject, query, 25).stream().map(value ->
                value.canonicalPath() + " [" + value.type() + "] = " + value.currentValue()).toList();
        return completed(CommandResponse.success("config.search", lines.isEmpty()
                ? List.of("No schema-owned settings matched '" + query + "'.") : lines));
    }

    private CompletionStage<CommandResponse> configList(PermissionSubject subject, List<String> arguments) {
        requireSize(arguments, 4, "config list <draft-id> <canonical-path>");
        List<String> values = configuration.listValues(subject, uuid(arguments.get(2), "draft ID"),
                arguments.get(3));
        return completed(CommandResponse.success("config.list", values.isEmpty()
                ? List.of("The selected structure is empty.") : values));
    }

    private CompletionStage<CommandResponse> configDraft(PermissionSubject subject, List<String> arguments) {
        requireSize(arguments, 2, "config draft");
        UUID draft = configuration.beginDraft(subject, "command");
        return completed(CommandResponse.success("config.draft.created", List.of("Draft: " + draft,
                "Production remains unchanged until this draft is previewed, validated, and applied.")));
    }

    private CompletionStage<CommandResponse> configSet(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() < 5) {
            throw usage("config set <draft-id> <canonical-path> <value>");
        }
        UUID draft = uuid(arguments.get(2), "draft ID");
        String path = arguments.get(3);
        configuration.editScalar(subject, draft, path, join(arguments, 4));
        return completed(CommandResponse.success("config.draft.edited", List.of(
                "Draft " + draft + " updated at " + path + ".", "Production configuration was not changed.")));
    }

    private CompletionStage<CommandResponse> configAdd(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() < 5) {
            throw usage("config add <draft-id> <canonical-path> <value>");
        }
        UUID draft = uuid(arguments.get(2), "draft ID");
        String path = arguments.get(3);
        if (path.equals("progression.stages")) {
            if (arguments.size() != 6 && arguments.size() != 8) {
                throw usage("config add <draft-id> progression.stages <stage-id> <display-name> "
                        + "[provider-id existing-group]");
            }
            Optional<ProviderId> provider = arguments.size() == 8
                    ? Optional.of(new ProviderId(arguments.get(6))) : Optional.empty();
            Optional<String> group = arguments.size() == 8 ? Optional.of(arguments.get(7)) : Optional.empty();
            configuration.addStage(subject, draft, new StageId(arguments.get(4)), arguments.get(5), provider, group);
        } else {
            requireSize(arguments, 5, "config add <draft-id> <list-path> <value>");
            configuration.addListValue(subject, draft, path, arguments.get(4));
        }
        return completed(CommandResponse.success("config.draft.structure_added", List.of(
                "Draft " + draft + " was structurally updated through the canonical editor.",
                "Production remains unchanged until preview and apply.")));
    }

    private CompletionStage<CommandResponse> configRemove(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() < 4 || arguments.size() > 5) {
            throw usage("config remove <draft-id> <canonical-path> [value-or-replacement-stage]");
        }
        UUID draft = uuid(arguments.get(2), "draft ID");
        String path = arguments.get(3);
        if (path.matches("progression\\.stages\\.[a-z][a-z0-9_-]{0,63}")) {
            String id = path.substring("progression.stages.".length());
            configuration.removeStage(subject, draft, new StageId(id), arguments.size() == 5
                    ? Optional.of(new StageId(arguments.get(4))) : Optional.empty());
        } else {
            requireSize(arguments, 5, "config remove <draft-id> <list-path> <value>");
            configuration.removeListValue(subject, draft, path, arguments.get(4));
        }
        return completed(CommandResponse.success("config.draft.structure_removed", List.of(
                "Draft " + draft + " was structurally updated through the canonical editor.",
                "Preview shows any migration and acknowledgement requirement.")));
    }

    private CompletionStage<CommandResponse> configPreview(PermissionSubject subject, List<String> arguments) {
        requireSize(arguments, 3, "config validate <draft-id>");
        return configuration.preview(subject, uuid(arguments.get(2), "draft ID"))
                .thenApply(PhaseSixCommandService::render);
    }

    private CompletionStage<CommandResponse> configRemap(PermissionSubject subject, List<String> arguments) {
        requireSize(arguments, 5, "config remap <draft-id> <missing-stage> <replacement-stage>");
        UUID draft = uuid(arguments.get(2), "draft ID");
        configuration.selectStageRemap(subject, draft, new StageId(arguments.get(3)),
                new StageId(arguments.get(4)));
        return completed(CommandResponse.success("config.draft.remap_selected", List.of(
                "Draft " + draft + " now requests " + arguments.get(3) + " → " + arguments.get(4) + ".",
                "Preview will validate and seal current references before acknowledgement/apply.")));
    }

    private CompletionStage<CommandResponse> configUnmap(PermissionSubject subject, List<String> arguments) {
        requireSize(arguments, 4, "config unmap <draft-id> <missing-stage>");
        UUID draft = uuid(arguments.get(2), "draft ID");
        configuration.removeStageRemap(subject, draft, new StageId(arguments.get(3)));
        return completed(CommandResponse.success("config.draft.remap_removed", List.of(
                "Draft " + draft + " no longer remaps source stage " + arguments.get(3) + ".",
                "Other source mappings remain intact; preview authority was invalidated.")));
    }

    private CompletionStage<CommandResponse> configAcknowledge(
            PermissionSubject subject,
            List<String> arguments) {
        requireSize(arguments, 3, "config acknowledge <draft-id>");
        var prepared = configuration.prepareAcknowledgement(subject, uuid(arguments.get(2), "draft ID"));
        ArrayList<String> lines = new ArrayList<>();
        prepared.findings().forEach(finding -> lines.add(finding.path() + " [" + finding.code() + "] "
                + finding.explanation() + " Consequence: " + finding.consequence()));
        lines.add("Server acknowledgement: " + prepared.acknowledgementId());
        lines.add("Expires: " + prepared.expiresAt());
        return completed(CommandResponse.success("config.acknowledgement.prepared", lines));
    }

    private CompletionStage<CommandResponse> configConfirm(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() < 4) {
            throw usage("config confirm <acknowledgement-id> <reason>");
        }
        return configuration.confirmAcknowledgement(subject, uuid(arguments.get(2), "acknowledgement ID"),
                join(arguments, 3)).thenApply(value -> CommandResponse.success("config.applied", List.of(
                        "Applied acknowledged revision " + value.id().value() + ".", "Status: " + value.status())));
    }

    private CompletionStage<CommandResponse> configCancel(PermissionSubject subject, List<String> arguments) {
        requireSize(arguments, 3, "config cancel <draft-id>");
        UUID draft = uuid(arguments.get(2), "draft ID");
        configuration.discardDraft(subject, draft);
        return completed(CommandResponse.success("config.draft.cancelled", List.of(
                "Draft " + draft + " was discarded. Production configuration was not changed.")));
    }

    private CompletionStage<CommandResponse> configHistory(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() > 3) {
            throw usage("config history [limit]");
        }
        int limit = arguments.size() == 3 ? positiveInteger(arguments.get(2), "history limit") : 20;
        List<String> lines = configuration.history(subject, limit).stream().map(revision ->
                revision.id().value() + " " + revision.status() + " by "
                        + revision.actor().displayName() + " — " + revision.reason()).toList();
        return completed(CommandResponse.success("config.history", lines.isEmpty()
                ? List.of("No configuration revisions have been recorded.") : lines));
    }

    private CompletionStage<CommandResponse> configRollback(PermissionSubject subject, List<String> arguments) {
        requireSize(arguments, 3, "config rollback <revision-id>");
        UUID draft = configuration.beginRollback(subject, new ConfigRevisionId(arguments.get(2)), "command");
        return configuration.preview(subject, draft).thenApply(preview -> {
            ArrayList<String> lines = new ArrayList<>(renderPreview(preview));
            lines.add("Rollback draft: " + draft);
            lines.add("Applying it creates a new revision; history is never rewritten.");
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
                "Applied new revision " + value.id().value() + ".", "Status: " + value.status())));
    }

    private CompletionStage<CommandResponse> setup(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() < 2) {
            throw usage("setup <discover|start|provider|stage|baseline|requirement|cost|reward|prestige|preview|"
                    + "acknowledge|confirm|apply|cancel> ...");
        }
        return switch (arguments.get(1).toLowerCase(java.util.Locale.ROOT)) {
            case "discover" -> setupDiscover(subject, arguments);
            case "start" -> setupStart(subject, arguments);
            case "provider" -> setupProvider(subject, arguments);
            case "stage" -> setupStage(subject, arguments);
            case "baseline" -> setupBaseline(subject, arguments);
            case "requirement" -> setupRequirement(subject, arguments);
            case "cost" -> setupCost(subject, arguments);
            case "reward" -> setupReward(subject, arguments);
            case "prestige" -> setupPrestige(subject, arguments);
            case "preview" -> setupPreview(subject, arguments);
            case "acknowledge" -> setupAcknowledge(subject, arguments);
            case "confirm" -> setupConfirm(subject, arguments);
            case "apply" -> setupApply(subject, arguments);
            case "cancel" -> setupCancel(subject, arguments);
            default -> throw usage("setup <discover|start|provider|stage|baseline|requirement|cost|reward|prestige|"
                    + "preview|acknowledge|confirm|apply|cancel> ...");
        };
    }

    private CompletionStage<CommandResponse> setupDiscover(PermissionSubject subject, List<String> arguments) {
        requireSize(arguments, 2, "setup discover");
        var discovery = setup.discover(subject);
        ArrayList<String> lines = new ArrayList<>();
        lines.add("Active V2 configuration present: " + discovery.activeConfigurationPresent());
        discovery.providers().forEach(provider -> lines.add(provider.providerId().value() + ": active="
                + provider.active() + ", healthy=" + provider.healthy() + ", rank=" + provider.rankCapable()
                + ", metrics=" + provider.metricIds()));
        lines.add(discovery.externalGroupPolicy());
        return completed(CommandResponse.success("setup.discovery", lines));
    }

    private CompletionStage<CommandResponse> setupStart(PermissionSubject subject, List<String> arguments) {
        requireSize(arguments, 2, "setup start");
        return completed(CommandResponse.success("setup.started", List.of("Setup session: " + setup.start(subject),
                "Choose an existing rank provider or internal, then add at least two stages.")));
    }

    private CompletionStage<CommandResponse> setupProvider(PermissionSubject subject, List<String> arguments) {
        requireSize(arguments, 4, "setup provider <session-id> <provider-id|internal>");
        setup.selectRankProvider(subject, uuid(arguments.get(2), "setup session"),
                arguments.get(3).equalsIgnoreCase("internal")
                        ? Optional.empty() : Optional.of(new ProviderId(arguments.get(3))));
        return completed(CommandResponse.success("setup.provider.selected", List.of(
                "Provider selection saved in the draft session; no external groups were created.")));
    }

    private CompletionStage<CommandResponse> setupStage(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() < 5 || arguments.size() > 6) {
            throw usage("setup stage <session-id> <stage-id> <display-name> [existing-group]");
        }
        setup.addStage(subject, uuid(arguments.get(2), "setup session"), new SetupStage(
                new StageId(arguments.get(3)), arguments.get(4),
                arguments.size() == 6 ? Optional.of(arguments.get(5)) : Optional.empty()));
        return completed(CommandResponse.success("setup.stage.added", List.of(
                "Stage added to the resumable setup draft; production remains unchanged.")));
    }

    private CompletionStage<CommandResponse> setupBaseline(PermissionSubject subject, List<String> arguments) {
        requireSize(arguments, 4, "setup baseline <session-id> <stage-id>");
        setup.selectBaseline(subject, uuid(arguments.get(2), "setup session"), new StageId(arguments.get(3)));
        return completed(CommandResponse.success("setup.baseline.selected", List.of("Setup baseline selected.")));
    }

    private CompletionStage<CommandResponse> setupRequirement(
            PermissionSubject subject,
            List<String> arguments) {
        requireSize(arguments, 10,
                "setup requirement <session> <id> <provider> <metric> <operator> <target> <scope> <completion>");
        setup.configureRequirement(subject, uuid(arguments.get(2), "setup session"), new SetupRequirement(
                new RequirementId(arguments.get(3)), new ProviderId(arguments.get(4)),
                new MetricId(arguments.get(5)), arguments.get(6), arguments.get(7), arguments.get(8),
                arguments.get(9)));
        return completed(CommandResponse.success("setup.requirement.configured",
                List.of("Simple eligibility requirement configured; preview will validate provider semantics.")));
    }

    private CompletionStage<CommandResponse> setupCost(PermissionSubject subject, List<String> arguments) {
        requireSize(arguments, 9,
                "setup cost <session> <id> <provider> <type> <value-type> <amount> <display-name>");
        setup.configureCost(subject, uuid(arguments.get(2), "setup session"), new SetupCost(
                new CostId(arguments.get(3)), new ProviderId(arguments.get(4)), arguments.get(5), arguments.get(6),
                arguments.get(7), arguments.get(8)));
        return completed(CommandResponse.success("setup.cost.configured",
                List.of("Simple stage cost configured; preview will validate it.")));
    }

    private CompletionStage<CommandResponse> setupReward(PermissionSubject subject, List<String> arguments) {
        requireSize(arguments, 9,
                "setup reward <session> <id> <provider> <type> <value-type> <value> <display-name>");
        setup.configureReward(subject, uuid(arguments.get(2), "setup session"), new SetupReward(
                new RewardId(arguments.get(3)), new ProviderId(arguments.get(4)), arguments.get(5), arguments.get(6),
                arguments.get(7), arguments.get(8)));
        return completed(CommandResponse.success("setup.reward.configured",
                List.of("Simple stage reward configured; preview will validate it.")));
    }

    private CompletionStage<CommandResponse> setupPrestige(PermissionSubject subject, List<String> arguments) {
        UUID sessionId = uuid(arguments.size() > 2 ? arguments.get(2) : "", "setup session");
        if (arguments.size() == 4 && arguments.get(3).equalsIgnoreCase("disabled")) {
            setup.configurePrestige(subject, sessionId, SetupPrestige.disabled());
        } else if (arguments.size() == 6 && arguments.get(3).equalsIgnoreCase("enabled")) {
            setup.configurePrestige(subject, sessionId, new SetupPrestige(true,
                    Optional.of(new StageId(arguments.get(4))), Optional.of(new StageId(arguments.get(5)))));
        } else {
            throw usage("setup prestige <session> disabled | setup prestige <session> enabled <required-stage> "
                    + "<reset-stage>");
        }
        return completed(CommandResponse.success("setup.prestige.configured",
                List.of("Prestige eligibility and reset behavior configured; preview the consequences.")));
    }

    private CompletionStage<CommandResponse> setupPreview(PermissionSubject subject, List<String> arguments) {
        requireSize(arguments, 3, "setup preview <session-id>");
        return setup.preview(subject, uuid(arguments.get(2), "setup session")).thenApply(value -> {
            ArrayList<String> lines = new ArrayList<>(renderPreview(value.configuration()));
            lines.addAll(value.playerExperience());
            return CommandResponse.success("setup.preview", lines);
        });
    }

    private CompletionStage<CommandResponse> setupApply(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() < 4) {
            throw usage("setup apply <session-id> <reason>");
        }
        return setup.apply(subject, uuid(arguments.get(2), "setup session"), Set.of(), join(arguments, 3))
                .thenApply(value -> CommandResponse.success("setup.applied", List.of(
                        "Setup applied as revision " + value.id().value() + ".")));
    }

    private CompletionStage<CommandResponse> setupAcknowledge(PermissionSubject subject, List<String> arguments) {
        requireSize(arguments, 3, "setup acknowledge <session-id>");
        var prepared = setup.prepareAcknowledgement(subject, uuid(arguments.get(2), "setup session"));
        ArrayList<String> lines = new ArrayList<>();
        prepared.findings().forEach(finding -> lines.add(finding.path() + " [" + finding.code() + "] "
                + finding.explanation() + " Consequence: " + finding.consequence()));
        lines.add("Server acknowledgement: " + prepared.acknowledgementId());
        lines.add("Confirm with /maddprestige setup confirm <token> <reason> before expiry "
                + prepared.expiresAt());
        return completed(CommandResponse.success("setup.acknowledgement.prepared", lines));
    }

    private CompletionStage<CommandResponse> setupConfirm(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() < 4) {
            throw usage("setup confirm <server-token> <reason>");
        }
        return setup.confirmAcknowledgement(subject, uuid(arguments.get(2), "setup acknowledgement"),
                join(arguments, 3)).thenApply(value -> CommandResponse.success("setup.applied", List.of(
                        "Setup applied as revision " + value.id().value() + ".")));
    }

    private CompletionStage<CommandResponse> setupCancel(PermissionSubject subject, List<String> arguments) {
        requireSize(arguments, 3, "setup cancel <session-id>");
        setup.cancel(subject, uuid(arguments.get(2), "setup session"));
        return completed(CommandResponse.success("setup.cancelled", List.of(
                "Setup draft cancelled; production configuration was not changed.")));
    }

    private CompletionStage<CommandResponse> doctor(PermissionSubject subject) {
        return doctor.inspect(subject).thenApply(report -> {
            ArrayList<String> lines = new ArrayList<>();
            lines.add("Doctor: " + report.status());
            report.findings().forEach(finding -> lines.add(finding.severity() + " " + finding.path()
                    + " — " + finding.summary() + " Next: " + finding.remediation()));
            return CommandResponse.success("doctor", lines);
        });
    }

    private CompletionStage<CommandResponse> staff(PermissionSubject subject, List<String> arguments) {
        if (arguments.size() < 8 || !arguments.get(1).equalsIgnoreCase("prestige")
                || !arguments.get(2).equalsIgnoreCase("set")) {
            throw usage("staff prestige set <player> <expected-state-revision> <current> <lifetime> <reason>");
        }
        UUID playerId = uuid(arguments.get(3), "player UUID");
        long expected = nonNegativeLong(arguments.get(4), "expected state revision");
        long current = nonNegativeLong(arguments.get(5), "current Prestige");
        long lifetime = nonNegativeLong(arguments.get(6), "lifetime Prestige");
        return prestigeAdministration.set(subject, playerId, expected, current, lifetime, "command",
                join(arguments, 7)).thenApply(state -> CommandResponse.success("staff.prestige.adjusted", List.of(
                        "Player " + playerId + " Prestige is now " + state.currentPrestige() + " current / "
                                + state.lifetimePrestige() + " lifetime at state revision " + state.stateRevision()
                                + ".")));
    }

    private static CommandResponse render(ConfigurationPreview preview) {
        return CommandResponse.success("config.preview", renderPreview(preview));
    }

    private static List<String> renderPreview(ConfigurationPreview preview) {
        ArrayList<String> lines = new ArrayList<>();
        lines.add("Draft " + preview.draftId() + "; base "
                + preview.baseRevision().map(ConfigRevisionId::value).orElse("none") + "; hash "
                + preview.candidateHash().value());
        lines.add("Draft version: " + preview.draftVersion());
        lines.add("Changed documents: " + String.join(", ", preview.changedDocuments()));
        lines.add("Validation: " + (preview.validation().hasErrors() ? "BLOCKED" : "VALID"));
        preview.validation().findings().forEach(finding -> lines.add(finding.severity() + " " + finding.path()
                + " [" + finding.code() + "] " + finding.explanation() + " Next: " + finding.remediation()));
        if (preview.stale()) {
            lines.add("STALE: active revision changed; apply will fail closed.");
        }
        if (!preview.stageImpact().semanticDiff().entries().isEmpty()) {
            preview.stageImpact().semanticDiff().entries().forEach(entry -> lines.add("DIFF " + entry.kind() + " "
                    + entry.path() + ": " + entry.redactedOldValue().orElse("<absent>") + " -> "
                    + entry.redactedNewValue().orElse("<absent>")));
        }
        if (!preview.stageImpact().affectedPlayerReferences().isEmpty()) {
            lines.add("Affected current player stages: " + preview.stageImpact().affectedPlayerReferences());
            lines.add("Sealed remap: " + preview.stageRemapSeal().map(value -> value.value()).orElse("missing"));
        }
        return List.copyOf(lines);
    }

    private static List<String> render(WhyReport report) {
        ArrayList<String> lines = new ArrayList<>();
        lines.add(report.executable() ? "Canonical authorization currently allows this operation."
                : "Canonical authorization blocks this operation.");
        if (!report.blockers().isEmpty()) {
            lines.addAll(report.blockers());
        }
        report.requirementExplanation().ifPresent(value -> lines.add("Requirements: " + value));
        report.configRevision().ifPresent(value -> lines.add("Configuration revision: " + value.value()));
        return List.copyOf(lines);
    }

    private static String render(OperationPreview preview) {
        String blockers = preview.blockers().isEmpty() ? "none" : String.join("; ", preview.blockers());
        return preview.kind() + " " + preview.stateChange() + "; costs=" + preview.costs() + "; rewards="
                + preview.rewards() + "; blockers=" + blockers + "; revision=" + preview.configRevision().value();
    }

    private static CommandResponse failure(Throwable failure) {
        Throwable cause = unwrap(failure);
        if (cause instanceof AdministrationException exception) {
            return CommandResponse.failure(exception.code(), safeMessage(exception), exception.remediation());
        }
        if (cause instanceof IllegalArgumentException exception) {
            return CommandResponse.failure("command.invalid", safeMessage(exception), USAGE);
        }
        return CommandResponse.failure("command.failed", "The command could not complete safely.",
                "Run doctor and consult server logs with the generated diagnostic code.");
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
        return new IllegalArgumentException("Usage: /maddprestige " + usage);
    }

    private static String join(List<String> arguments, int start) {
        return String.join(" ", arguments.subList(start, arguments.size()));
    }

    private static String safeMessage(Throwable failure) {
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private static CompletionStage<CommandResponse> completed(CommandResponse response) {
        return CompletableFuture.completedFuture(response);
    }
}
