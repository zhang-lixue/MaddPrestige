package net.maddkraft.maddprestige.core.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.core.admin.command.CommandInvocation;
import net.maddkraft.maddprestige.core.admin.command.ContextualHelpService;
import net.maddkraft.maddprestige.core.admin.command.PhaseSixCommandService;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationAdministrationService;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationApplicationStatus;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationHistoryStore;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationIntrospectionService;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationSnapshotStore;
import net.maddkraft.maddprestige.core.admin.config.PhaseSixConfigurationWorkflow;
import net.maddkraft.maddprestige.core.admin.config.PreparedConfigurationSnapshot;
import net.maddkraft.maddprestige.core.admin.config.StoredConfigurationRevision;
import net.maddkraft.maddprestige.core.admin.diagnostic.DoctorService;
import net.maddkraft.maddprestige.core.admin.diagnostic.WhyService;
import net.maddkraft.maddprestige.core.admin.player.PlayerProgressViewService;
import net.maddkraft.maddprestige.core.admin.presentation.MessageReference;
import net.maddkraft.maddprestige.core.admin.setup.SetupWizardService;
import net.maddkraft.maddprestige.core.admin.ui.GuiSessionService;
import net.maddkraft.maddprestige.core.admin.ui.CanonicalGuiMutationExecutor;
import net.maddkraft.maddprestige.core.config.BackupMetadata;
import net.maddkraft.maddprestige.core.config.ConfigDraft;
import net.maddkraft.maddprestige.core.config.CompiledConfiguration;
import net.maddkraft.maddprestige.core.config.ConfigurationService;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.core.plan.RankUpAuthorizationResult;
import net.maddkraft.maddprestige.core.plan.RankUpExecutionResult;
import net.maddkraft.maddprestige.core.plan.RankUpExecutionStatus;
import net.maddkraft.maddprestige.core.prestige.PlayerPrestigeState;
import net.maddkraft.maddprestige.core.prestige.PrestigeAuthorizationResult;
import net.maddkraft.maddprestige.core.prestige.PrestigeExecutionResult;
import net.maddkraft.maddprestige.core.prestige.PrestigeExecutionStatus;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.core.schema.PhaseSixSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhaseSixCommandServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-16T12:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("[OR8D-02] Manual Prestige mutation establishes lifecycle before its audited CAS")
    void manualPrestigeMutationUsesLifecycleBoundaryBeforeStore() {
        UUID playerId = UUID.randomUUID();
        AtomicInteger initializations = new AtomicInteger();
        AtomicInteger adjustments = new AtomicInteger();
        ConfigRevisionId revision = new ConfigRevisionId("manual_lifecycle_revision");
        ManualPrestigeAdministrationService service = new ManualPrestigeAdministrationService(adjustment -> {
            assertEquals(1, initializations.get());
            adjustments.incrementAndGet();
            return new PlayerPrestigeState(adjustment.playerId(), adjustment.currentPrestige(),
                    adjustment.lifetimePrestige(), adjustment.expectedStateRevision() + 1,
                    adjustment.configRevision(), new net.maddkraft.maddprestige.api.id.ScopeId("manual"),
                    Optional.empty(), CLOCK.instant(), CLOCK.instant());
        }, () -> revision, Runnable::run, requested -> {
            assertEquals(playerId, requested);
            initializations.incrementAndGet();
            return Optional.empty();
        });

        PlayerPrestigeState result = service.set(subject(PhaseSixPermissions.PLAYER_PRESTIGE_EDIT), playerId,
                0, 2, 3, "command", "owner correction").toCompletableFuture().join();

        assertEquals(1, initializations.get());
        assertEquals(1, adjustments.get());
        assertEquals(2, result.currentPrestige());
        assertEquals(3, result.lifetimePrestige());
    }

    @Test
    @DisplayName("[A42][A44][A56] Command router exposes canonical help/read paths and denies equivalent mutation")
    void routesCanonicalServicesWithStableActionableErrors() {
        Fixture fixture = new Fixture();
        PermissionSubject reader = subject(PhaseSixPermissions.USE, PhaseSixPermissions.CONFIG_VIEW);

        var help = fixture.commands.execute(new CommandInvocation(reader, List.of("help", "measurement")))
                .toCompletableFuture().join();
        var explain = fixture.commands.execute(new CommandInvocation(reader,
                List.of("config", "explain", "prestige.enabled"))).toCompletableFuture().join();
        var denied = fixture.commands.execute(new CommandInvocation(reader, List.of("config", "draft")))
                .toCompletableFuture().join();

        assertTrue(help.successful());
        assertTrue(help.messages().stream().flatMap(message -> message.arguments().values().stream())
                .anyMatch(value -> value.contains("since-prestige-start")));
        assertTrue(explain.successful());
        assertTrue(explain.messages().stream().anyMatch(message -> message.key().equals("command.config.value")
                && message.argument("path").filter("prestige.enabled"::equals).isPresent()
                && message.argument("value").filter("false"::equals).isPresent()));
        assertTrue(explain.messages().stream().anyMatch(message ->
                message.key().equals("command.config.description.prestige_enabled")));
        assertFalse(denied.successful());
        assertEquals("permission.denied", denied.code());
        assertEquals(List.of("command.error.administration.permission_denied.summary",
                "command.error.administration.permission_denied.remediation"),
                denied.messages().stream().map(MessageReference::key).toList());
        assertTrue(denied.messages().stream().allMatch(message ->
                message.argument("permission").filter(PhaseSixPermissions.CONFIG_EDIT::equals).isPresent()));
    }

    @Test
    @DisplayName("[Phase6-security] Unknown/unauthorized commands return bounded codes without internal failures")
    void normalizesCommandFailures() {
        Fixture fixture = new Fixture();
        PermissionSubject player = subject(PhaseSixPermissions.USE);

        var unknown = fixture.commands.execute(new CommandInvocation(player, List.of("not-a-command")))
                .toCompletableFuture().join();
        var doctor = fixture.commands.execute(new CommandInvocation(player, List.of("doctor")))
                .toCompletableFuture().join();
        var usage = fixture.commands.execute(new CommandInvocation(player, List.of("simulate")))
                .toCompletableFuture().join();

        assertEquals("command.unknown", unknown.code());
        assertEquals("permission.denied", doctor.code());
        assertEquals("command.invalid", usage.code());
        assertEquals("command.error.usage", usage.messages().getFirst().key());
        assertEquals("/maddprestige simulate <rankup|prestige> [player-uuid]",
                usage.messages().getFirst().argument("usage").orElseThrow());
        assertTrue(unknown.lines().stream().noneMatch(line -> line.contains("Exception")));
        assertTrue(doctor.lines().stream().noneMatch(line -> line.contains("Exception")));
    }

    @Test
    @DisplayName("[A45][A46][A47][A68][OR8D-07] Command boundary emits actionable Doctor and exact Why semantics")
    void commandBoundaryPreservesDoctorAndWhySemantics() {
        Fixture fixture = new Fixture();
        PermissionSubject owner = subject(PhaseSixPermissions.all().toArray(String[]::new));
        UUID player = UUID.randomUUID();

        var why = fixture.commands.execute(new CommandInvocation(owner,
                List.of("why", "rankup", player.toString()))).toCompletableFuture().join();
        var doctor = fixture.commands.execute(new CommandInvocation(owner, List.of("doctor")))
                .toCompletableFuture().join();

        assertTrue(why.successful());
        assertTrue(why.messages().stream().anyMatch(message ->
                message.key().equals("command.why.blocker.no_active_stage_snapshot")));
        assertTrue(doctor.successful());
        assertTrue(doctor.messages().stream().anyMatch(message -> message.key().endsWith(".summary")));
        assertTrue(doctor.messages().stream().anyMatch(message -> message.key().endsWith(".remediation")));
        assertTrue(doctor.messages().stream().filter(message -> message.key().endsWith(".summary"))
                .allMatch(message -> message.argument("path").isPresent()
                        && message.argument("code").isPresent()));
    }

    @Test
    @DisplayName("[A42] Direct, command, and GUI structural edits produce the exact same canonical candidate")
    void commandAndGuiUseExactCanonicalStructuralMutationPath() {
        Fixture fixture = new Fixture();
        PermissionSubject owner = subject(PhaseSixPermissions.all().toArray(String[]::new));
        UUID directDraft = fixture.configuration.beginDraft(owner, "direct");
        UUID commandDraft = fixture.configuration.beginDraft(owner, "command");
        UUID guiDraft = fixture.configuration.beginDraft(owner, "gui");

        fixture.configuration.editScalar(owner, directDraft, "prestige.enabled", "true");
        fixture.configuration.addStage(owner, directDraft, new net.maddkraft.maddprestige.api.id.StageId("member"),
                "Member", Optional.empty(), Optional.empty());

        var commandSet = fixture.commands.execute(new CommandInvocation(owner,
                List.of("config", "set", commandDraft.toString(), "prestige.enabled", "true")))
                .toCompletableFuture().join();
        var commandAdd = fixture.commands.execute(new CommandInvocation(owner,
                List.of("config", "add", commandDraft.toString(), "progression.stages", "member", "Member")))
                .toCompletableFuture().join();

        var scalarGui = fixture.gui.openScalarEditor(owner, guiDraft, "prestige.enabled", "true");
        fixture.gui.click(owner, scalarGui.sessionId(), scalarGui.actions().getFirst().actionId())
                .toCompletableFuture().join();
        var stageGui = fixture.gui.openStageAdder(owner, guiDraft,
                new net.maddkraft.maddprestige.api.id.StageId("member"), "Member", Optional.empty(),
                Optional.empty());
        fixture.gui.click(owner, stageGui.sessionId(), stageGui.actions().getFirst().actionId())
                .toCompletableFuture().join();

        var direct = fixture.configuration.preview(owner, directDraft).toCompletableFuture().join();
        var command = fixture.configuration.preview(owner, commandDraft).toCompletableFuture().join();
        var gui = fixture.configuration.preview(owner, guiDraft).toCompletableFuture().join();

        assertTrue(commandSet.successful());
        assertTrue(commandAdd.successful());
        assertEquals(direct.candidateHash(), command.candidateHash());
        assertEquals(direct.candidateHash(), gui.candidateHash());
        assertEquals(direct.changedDocuments(), command.changedDocuments());
        assertEquals(direct.changedDocuments(), gui.changedDocuments());
    }

    @Test
    @DisplayName("[A42] Direct, command, and GUI apply and rollback preserve canonical state and history semantics")
    void commandAndGuiApplyAndRollbackThroughCanonicalServices() {
        PermissionSubject owner = subject(PhaseSixPermissions.all().toArray(String[]::new));
        Fixture direct = new Fixture();
        Fixture command = new Fixture();
        Fixture gui = new Fixture();

        ConfigRevisionId directFirst = directApply(direct, owner, "2");
        ConfigRevisionId commandFirst = commandApply(command, owner, "2");
        ConfigRevisionId guiFirst = guiApply(gui, owner, "2");
        Map<String, String> firstDocuments = activeDocuments(direct);
        assertEquals(firstDocuments, activeDocuments(command));
        assertEquals(firstDocuments, activeDocuments(gui));

        ConfigRevisionId directSecond = directApply(direct, owner, "3");
        ConfigRevisionId commandSecond = commandApply(command, owner, "3");
        ConfigRevisionId guiSecond = guiApply(gui, owner, "3");

        directRollback(direct, owner, directFirst, directSecond);
        commandRollback(command, owner, commandFirst, commandSecond);
        guiRollback(gui, owner, guiFirst, guiSecond);

        assertEquals(firstDocuments, activeDocuments(direct));
        assertEquals(firstDocuments, activeDocuments(command));
        assertEquals(firstDocuments, activeDocuments(gui));
        assertEquivalentAppliedHistory(direct.history);
        assertEquivalentAppliedHistory(command.history);
        assertEquivalentAppliedHistory(gui.history);
    }

    @Test
    @DisplayName("[A41][A42][A56] High-risk GUI rollback acknowledgement derives rollback kind and permission")
    void highRiskGuiRollbackUsesSealedRollbackAuthorityEndToEnd() {
        PermissionSubject owner = subject(PhaseSixPermissions.all().toArray(String[]::new));
        Fixture fixture = new Fixture();
        ConfigRevisionId targetWithoutMember = directApply(fixture, owner, "2");

        UUID addDraft = fixture.configuration.beginDraft(owner, "gui");
        fixture.configuration.addStage(owner, addDraft, new StageId("member"), "Member",
                Optional.empty(), Optional.empty());
        fixture.configuration.preview(owner, addDraft).toCompletableFuture().join();
        var addAcknowledgement = fixture.gui.openAcknowledgement(owner, addDraft);
        var addPrepared = fixture.gui.click(owner, addAcknowledgement.sessionId(),
                addAcknowledgement.actions().getFirst().actionId()).toCompletableFuture().join();
        UUID addToken = acknowledgementId(addPrepared);
        var addConfirmation = fixture.gui.openAcknowledgementConfirmation(owner, addToken, "add member");
        fixture.gui.click(owner, addConfirmation.sessionId(), addConfirmation.actions().getFirst().actionId())
                .toCompletableFuture().join();
        ConfigRevisionId currentWithMember = fixture.canonical.active().orElseThrow().revisionId();

        var selector = fixture.gui.openRollbackSelector(owner, targetWithoutMember);
        var selected = fixture.gui.click(owner, selector.sessionId(), selector.actions().getFirst().actionId())
                .toCompletableFuture().join();
        UUID rollbackDraft = UUID.fromString(selected.argument("draft").orElseThrow());
        var preview = fixture.gui.openDraftPreview(owner, rollbackDraft);
        fixture.gui.click(owner, preview.sessionId(), preview.actions().getFirst().actionId())
                .toCompletableFuture().join();
        assertEquals(Optional.of(currentWithMember), activeRevision(fixture));

        PermissionSubject normalOnly = new PermissionSubject(owner.actor(), Set.of(
                PhaseSixPermissions.ADMIN_GUI, PhaseSixPermissions.CONFIG_APPLY));
        assertEquals("permission.denied", assertThrows(AdministrationException.class,
                () -> fixture.gui.openAcknowledgement(normalOnly, rollbackDraft)).code());

        var rollbackAcknowledgement = fixture.gui.openAcknowledgement(owner, rollbackDraft);
        assertEquals(PhaseSixPermissions.CONFIG_ROLLBACK,
                rollbackAcknowledgement.actions().getFirst().requiredPermission());
        var rollbackPrepared = fixture.gui.click(owner, rollbackAcknowledgement.sessionId(),
                rollbackAcknowledgement.actions().getFirst().actionId()).toCompletableFuture().join();
        UUID rollbackToken = acknowledgementId(rollbackPrepared);
        var confirmation = fixture.gui.openAcknowledgementConfirmation(owner, rollbackToken,
                "remove member through rollback");
        assertEquals(PhaseSixPermissions.CONFIG_ROLLBACK,
                confirmation.actions().getFirst().requiredPermission());
        fixture.gui.click(owner, confirmation.sessionId(), confirmation.actions().getFirst().actionId())
                .toCompletableFuture().join();

        assertEquals(Optional.of(targetWithoutMember), fixture.history.recent(10).getLast().rollbackSource());
        assertFalse(activeDocuments(fixture).get("progression.yml").contains("  member:"));

        Fixture command = new Fixture();
        ConfigRevisionId commandTarget = directApply(command, owner, "2");
        UUID commandAddDraft = command.configuration.beginDraft(owner, "command");
        command.configuration.addStage(owner, commandAddDraft, new StageId("member"), "Member",
                Optional.empty(), Optional.empty());
        var commandAddPreview = command.configuration.preview(owner, commandAddDraft)
                .toCompletableFuture().join();
        var commandAddAck = command.commands.execute(new CommandInvocation(owner,
                List.of("config", "acknowledge", commandAddDraft.toString()))).toCompletableFuture().join();
        UUID commandAddToken = commandAcknowledgementId(commandAddAck);
        command.commands.execute(new CommandInvocation(owner,
                List.of("config", "confirm", commandAddToken.toString(), "add", "member")))
                .toCompletableFuture().join();
        var commandRollback = command.commands.execute(new CommandInvocation(owner,
                List.of("config", "rollback", commandTarget.value()))).toCompletableFuture().join();
        UUID commandRollbackDraft = UUID.fromString(commandRollback.messages().stream()
                .filter(message -> message.key().equals("command.config.rollback_draft"))
                .findFirst().orElseThrow().argument("draft").orElseThrow());
        var commandRollbackPreview = command.configuration.preview(owner, commandRollbackDraft)
                .toCompletableFuture().join();
        var commandRollbackAck = command.commands.execute(new CommandInvocation(owner,
                List.of("config", "acknowledge", commandRollbackDraft.toString()))).toCompletableFuture().join();
        UUID commandRollbackToken = commandAcknowledgementId(commandRollbackAck);
        command.commands.execute(new CommandInvocation(owner, List.of("config", "confirm",
                commandRollbackToken.toString(), "remove", "member", "through", "rollback")))
                .toCompletableFuture().join();

        assertEquals(commandAddPreview.validation().findings().stream().map(finding -> finding.code()).toList(),
                commandRollbackPreview.validation().findings().stream().map(finding -> finding.code()).toList());
        assertEquals(activeDocuments(command), activeDocuments(fixture));
        assertEquals(ConfigurationApplicationStatus.APPLIED, command.history.recent(10).getLast().status());
        assertTrue(command.history.recent(10).getLast().rollbackSource().isPresent());
    }

    @Test
    @DisplayName("[A42][A69] Command and GUI expose draft-bound remap selection that canonical preview revalidates")
    void commandAndGuiStageRemapSelectionAreDraftBoundAndFailClosed() {
        PermissionSubject owner = subject(PhaseSixPermissions.all().toArray(String[]::new));
        Fixture command = new Fixture();
        UUID commandDraft = command.configuration.beginDraft(owner, "command");
        addRemapTargets(command, owner, commandDraft);
        var selected = command.commands.execute(new CommandInvocation(owner, List.of("config", "remap",
                commandDraft.toString(), "missing_stage", "first")))
                .toCompletableFuture().join();
        assertTrue(selected.successful(), selected.lines().toString());
        var commandPreview = command.configuration.preview(owner, commandDraft)
                .toCompletableFuture().join();
        assertTrue(commandPreview.validation().findings().stream()
                .anyMatch(finding -> finding.code().equals("stage.change.remap_extra_source")));

        Fixture gui = new Fixture();
        UUID guiDraft = gui.configuration.beginDraft(owner, "gui");
        addRemapTargets(gui, owner, guiDraft);
        var selector = gui.gui.openStageRemapSelector(owner, guiDraft, new StageId("missing_stage"),
                new StageId("first"));
        var response = gui.gui.click(owner, selector.sessionId(), selector.actions().getFirst().actionId())
                .toCompletableFuture().join();
        assertEquals(guiDraft.toString(), response.argument("draft").orElseThrow());
        var guiPreview = gui.configuration.preview(owner, guiDraft).toCompletableFuture().join();
        assertEquals(commandPreview.validation().findings().stream().map(finding -> finding.code()).toList(),
                guiPreview.validation().findings().stream().map(finding -> finding.code()).toList());
    }

    @Test
    @DisplayName("[A41][A42][A69] Command and GUI accumulate, replace, and remove one server-owned remap source")
    void commandAndGuiMultiRemapStateIsEquivalentAndSourceScoped() {
        PermissionSubject owner = subject(PhaseSixPermissions.all().toArray(String[]::new));
        Fixture command = new Fixture();
        UUID commandDraft = command.configuration.beginDraft(owner, "command");
        addRemapTargets(command, owner, commandDraft);
        var first = command.commands.execute(new CommandInvocation(owner, List.of(
                "config", "remap", commandDraft.toString(), "missing_b", "first"))).toCompletableFuture().join();
        assertTrue(first.successful(), first.lines().toString());
        var second = command.commands.execute(new CommandInvocation(owner, List.of(
                "config", "remap", commandDraft.toString(), "missing_d", "second"))).toCompletableFuture().join();
        assertTrue(second.successful(), second.lines().toString());
        command.configuration.preview(owner, commandDraft).toCompletableFuture().join();
        Map<StageId, StageId> commandMappings = command.references.lastPlan.orElseThrow().mappings();
        assertEquals(Map.of(new StageId("missing_b"), new StageId("first"),
                new StageId("missing_d"), new StageId("second")), commandMappings);

        command.commands.execute(new CommandInvocation(owner, List.of(
                "config", "remap", commandDraft.toString(), "missing_b", "second"))).toCompletableFuture().join();
        command.configuration.preview(owner, commandDraft).toCompletableFuture().join();
        assertEquals(new StageId("second"), command.references.lastPlan.orElseThrow().mappings()
                .get(new StageId("missing_b")));
        assertEquals(new StageId("second"), command.references.lastPlan.orElseThrow().mappings()
                .get(new StageId("missing_d")));

        var invalid = command.commands.execute(new CommandInvocation(owner, List.of(
                "config", "remap", commandDraft.toString(), "missing_x", "absent")))
                .toCompletableFuture().join();
        assertFalse(invalid.successful());
        command.configuration.preview(owner, commandDraft).toCompletableFuture().join();
        assertEquals(2, command.references.lastPlan.orElseThrow().mappings().size());
        command.commands.execute(new CommandInvocation(owner, List.of(
                "config", "unmap", commandDraft.toString(), "missing_b"))).toCompletableFuture().join();
        command.configuration.preview(owner, commandDraft).toCompletableFuture().join();
        assertEquals(Map.of(new StageId("missing_d"), new StageId("second")),
                command.references.lastPlan.orElseThrow().mappings());

        Fixture gui = new Fixture();
        UUID guiDraft = gui.configuration.beginDraft(owner, "gui");
        addRemapTargets(gui, owner, guiDraft);
        click(gui, owner, gui.gui.openStageRemapSelector(owner, guiDraft,
                new StageId("missing_b"), new StageId("first")));
        click(gui, owner, gui.gui.openStageRemapSelector(owner, guiDraft,
                new StageId("missing_d"), new StageId("second")));
        gui.configuration.preview(owner, guiDraft).toCompletableFuture().join();
        assertEquals(commandMappings, gui.references.lastPlan.orElseThrow().mappings());
        click(gui, owner, gui.gui.openStageRemapRemoval(owner, guiDraft, new StageId("missing_b")));
        gui.configuration.preview(owner, guiDraft).toCompletableFuture().join();
        assertEquals(Map.of(new StageId("missing_d"), new StageId("second")),
                gui.references.lastPlan.orElseThrow().mappings());
    }

    private static void click(Fixture fixture, PermissionSubject owner,
            net.maddkraft.maddprestige.core.admin.ui.GuiSessionView view) {
        fixture.gui.click(owner, view.sessionId(), view.actions().getFirst().actionId()).toCompletableFuture().join();
    }

    private static void addRemapTargets(Fixture fixture, PermissionSubject owner, UUID draft) {
        fixture.configuration.addStage(owner, draft, new StageId("first"), "First", Optional.empty(),
                Optional.empty());
        ConfigDraft edited = fixture.configuration.addStage(owner, draft, new StageId("second"), "Second",
                Optional.empty(),
                Optional.empty());
        var compiled = new net.maddkraft.maddprestige.core.stage.StageConfigurationCompiler()
                .compile(new net.maddkraft.maddprestige.core.config.ConfigCompiler().compile(edited));
        var stages = compiled.configuration().orElseThrow();
        assertTrue(stages.stages().containsKey(new StageId("first"))
                        && stages.stages().get(new StageId("first")).enabled()
                        && stages.order().contains(new StageId("first")),
                stages + "\n" + edited.documents().get("progression.yml"));
    }

    private static ConfigRevisionId directApply(Fixture fixture, PermissionSubject owner, String value) {
        UUID draft = fixture.configuration.beginDraft(owner, "direct");
        fixture.configuration.editScalar(owner, draft, "prestige.current-count-increment", value);
        fixture.configuration.preview(owner, draft).toCompletableFuture().join();
        return fixture.configuration.applyDraft(owner, draft, activeRevision(fixture), Set.of(), "direct parity")
                .toCompletableFuture().join().id();
    }

    private static ConfigRevisionId commandApply(Fixture fixture, PermissionSubject owner, String value) {
        UUID draft = fixture.configuration.beginDraft(owner, "command");
        var set = fixture.commands.execute(new CommandInvocation(owner, List.of("config", "set", draft.toString(),
                "prestige.current-count-increment", value))).toCompletableFuture().join();
        var preview = fixture.commands.execute(new CommandInvocation(owner,
                List.of("config", "validate", draft.toString()))).toCompletableFuture().join();
        ConfigRevisionId expected = activeRevision(fixture).orElseThrow();
        var apply = fixture.commands.execute(new CommandInvocation(owner, List.of("config", "apply",
                draft.toString(), expected.value(), "command", "parity"))).toCompletableFuture().join();
        assertTrue(set.successful());
        assertTrue(preview.successful());
        assertTrue(apply.successful());
        return fixture.canonical.active().orElseThrow().revisionId();
    }

    private static ConfigRevisionId guiApply(Fixture fixture, PermissionSubject owner, String value) {
        UUID draft = fixture.configuration.beginDraft(owner, "gui");
        var editor = fixture.gui.openScalarEditor(owner, draft, "prestige.current-count-increment", value);
        fixture.gui.click(owner, editor.sessionId(), editor.actions().getFirst().actionId())
                .toCompletableFuture().join();
        var preview = fixture.gui.openDraftPreview(owner, draft);
        fixture.gui.click(owner, preview.sessionId(), preview.actions().getFirst().actionId())
                .toCompletableFuture().join();
        var apply = fixture.gui.openDraftApply(owner, draft, "gui parity");
        fixture.gui.click(owner, apply.sessionId(), apply.actions().getFirst().actionId())
                .toCompletableFuture().join();
        return fixture.canonical.active().orElseThrow().revisionId();
    }

    private static void directRollback(
            Fixture fixture,
            PermissionSubject owner,
            ConfigRevisionId target,
            ConfigRevisionId expected) {
        UUID draft = fixture.configuration.beginRollback(owner, target, "direct");
        fixture.configuration.preview(owner, draft).toCompletableFuture().join();
        fixture.configuration.applyRollback(owner, draft, Optional.of(expected), Set.of(), "direct rollback")
                .toCompletableFuture().join();
    }

    private static void commandRollback(
            Fixture fixture,
            PermissionSubject owner,
            ConfigRevisionId target,
            ConfigRevisionId expected) {
        var prepared = fixture.commands.execute(new CommandInvocation(owner,
                List.of("config", "rollback", target.value()))).toCompletableFuture().join();
        assertTrue(prepared.successful());
        UUID draft = UUID.fromString(prepared.messages().stream()
                .filter(message -> message.key().equals("command.config.rollback_draft"))
                .findFirst().orElseThrow().argument("draft").orElseThrow());
        var applied = fixture.commands.execute(new CommandInvocation(owner, List.of("config", "rollback-apply",
                draft.toString(), expected.value(), "command", "rollback"))).toCompletableFuture().join();
        assertTrue(applied.successful());
    }

    private static void guiRollback(
            Fixture fixture,
            PermissionSubject owner,
            ConfigRevisionId target,
            ConfigRevisionId expected) {
        var selector = fixture.gui.openRollbackSelector(owner, target);
        var prepared = fixture.gui.click(owner, selector.sessionId(), selector.actions().getFirst().actionId())
                .toCompletableFuture().join();
        UUID draft = UUID.fromString(prepared.argument("draft").orElseThrow());
        var preview = fixture.gui.openDraftPreview(owner, draft);
        fixture.gui.click(owner, preview.sessionId(), preview.actions().getFirst().actionId())
                .toCompletableFuture().join();
        assertEquals(Optional.of(expected), activeRevision(fixture));
        var apply = fixture.gui.openDraftApply(owner, draft, "gui rollback");
        fixture.gui.click(owner, apply.sessionId(), apply.actions().getFirst().actionId())
                .toCompletableFuture().join();
    }

    private static Optional<ConfigRevisionId> activeRevision(Fixture fixture) {
        return fixture.canonical.active().map(active -> active.revisionId());
    }

    private static Map<String, String> activeDocuments(Fixture fixture) {
        return fixture.canonical.active().orElseThrow().compiled().documents();
    }

    private static UUID acknowledgementId(
            net.maddkraft.maddprestige.core.admin.presentation.MessageReference response) {
        return UUID.fromString(response.argument("acknowledgement").orElseThrow());
    }

    private static UUID commandAcknowledgementId(
            net.maddkraft.maddprestige.core.admin.command.CommandResponse response) {
        return UUID.fromString(response.messages().stream()
                .filter(message -> message.key().equals("command.acknowledgement.id"))
                .findFirst().orElseThrow().argument("acknowledgement").orElseThrow());
    }

    private static void assertEquivalentAppliedHistory(MemoryHistory history) {
        assertEquals(3, history.recent(10).size());
        assertTrue(history.recent(10).stream()
                .allMatch(revision -> revision.status() == ConfigurationApplicationStatus.APPLIED));
        assertTrue(history.recent(10).getLast().rollbackSource().isPresent());
    }

    private static PermissionSubject subject(String... permissions) {
        return new PermissionSubject(new Actor("console", Optional.empty(), "Console"), Set.of(permissions));
    }

    private static net.maddkraft.maddprestige.core.authorization.AuthorizationBlocker blocker(
            net.maddkraft.maddprestige.core.authorization.AuthorizationBlockerKind kind,
            String diagnostic) {
        return net.maddkraft.maddprestige.core.authorization.AuthorizationBlocker.of(kind, diagnostic);
    }

    private static final class Fixture {
        private final ConfigRevisionId revision = new ConfigRevisionId("command_revision");
        private final ConfigurationService canonical = activeConfiguration();
        private final MemoryHistory history = new MemoryHistory();
        private final EmptyStageReferenceMigrationStore references = new EmptyStageReferenceMigrationStore();
        private final ConfigurationAdministrationService configuration;
        private final PhaseSixCommandService commands;
        private final GuiSessionService gui;

        private Fixture() {
            var schema = PhaseSixSchema.create();
            configuration = new ConfigurationAdministrationService(canonical,
                    new PhaseSixConfigurationWorkflow(canonical, new ProviderRegistry(),
                            references, List.of()), schema,
                    history, new MemorySnapshots(), CLOCK);
            var introspection = new ConfigurationIntrospectionService(schema, canonical::active,
                    () -> ValidationReport.VALID);
            var preview = new OperationPreviewService(intent -> CompletableFuture.completedFuture(
                    RankUpAuthorizationResult.rejected(blocker(
                            net.maddkraft.maddprestige.core.authorization.AuthorizationBlockerKind
                                    .NO_ACTIVE_STAGE_SNAPSHOT,
                            "not configured"))), intent -> CompletableFuture.completedFuture(
                                    PrestigeAuthorizationResult.rejected(blocker(
                                            net.maddkraft.maddprestige.core.authorization.AuthorizationBlockerKind
                                                    .NO_ACTIVE_PRESTIGE_CONFIGURATION,
                                            "not configured"))));
            var confirmation = new OperationConfirmationService(preview,
                    plan -> CompletableFuture.completedFuture(new RankUpExecutionResult(plan.operationId(),
                            RankUpExecutionStatus.FAILED, "unused")),
                    plan -> CompletableFuture.completedFuture(new PrestigeExecutionResult(OperationId.random(),
                            PrestigeExecutionStatus.FAILED, "unused")), () -> activeRevision(this),
                    Duration.ofMinutes(1), CLOCK);
            var doctor = new DoctorService(new ProviderRegistry(), canonical::active, List.of(), CLOCK);
            var why = new WhyService(intent -> CompletableFuture.completedFuture(
                    RankUpAuthorizationResult.rejected(blocker(
                            net.maddkraft.maddprestige.core.authorization.AuthorizationBlockerKind
                                    .NO_ACTIVE_STAGE_SNAPSHOT,
                            "not configured"))), intent -> CompletableFuture.completedFuture(
                                    PrestigeAuthorizationResult.rejected(blocker(
                                            net.maddkraft.maddprestige.core.authorization.AuthorizationBlockerKind
                                                    .NO_ACTIVE_PRESTIGE_CONFIGURATION,
                                            "not configured"))));
            var manual = new ManualPrestigeAdministrationService(adjustment -> new PlayerPrestigeState(
                    adjustment.playerId(), adjustment.currentPrestige(), adjustment.lifetimePrestige(),
                    adjustment.expectedStateRevision() + 1, adjustment.configRevision(),
                    new net.maddkraft.maddprestige.api.id.ScopeId("manual"), Optional.empty(), CLOCK.instant(),
                    CLOCK.instant()), () -> activeRevision(this).orElse(revision), Runnable::run);
            CanonicalGuiMutationExecutor guiMutations = new CanonicalGuiMutationExecutor(configuration);
            gui = new GuiSessionService(() -> activeRevision(this), guiMutations::execute, guiMutations,
                    Duration.ofMinutes(1), CLOCK);
            commands = new PhaseSixCommandService(new ContextualHelpService(schema), introspection, configuration,
                    doctor, why, preview, confirmation, new PlayerProgressViewService(preview),
                    new SetupWizardService(configuration), manual, gui, () -> activeRevision(this), Runnable::run);
        }

        private ConfigurationService activeConfiguration() {
            ConfigurationService service = new ConfigurationService();
            Map<String, String> documents = Map.of(
                    "progression.yml", resource("/defaults/progression.yml"),
                    "requirements.yml", resource("/defaults/requirements.yml"),
                    "rewards.yml", resource("/defaults/rewards.yml"),
                    "lifecycle.yml", resource("/defaults/lifecycle.yml"),
                    "integrations.yml", """
                            schema-version: 5
                            vault: {enabled: false}
                            mcmmo: {enabled: false}
                            placeholderapi: {output: {enabled: false}, inputs: {}}
                            economyshopgui: {compatibility-enabled: false, progression-credit: {enabled: false}}
                            quickshop: {compatibility-enabled: false, progression-credit: {enabled: false}}
                            """);
            CompiledConfiguration compiled = new CompiledConfiguration(RevisionHasher.hashDocuments(documents),
                    documents);
            service.apply(revision, compiled, ValidationReport.VALID, Set.of(), new BackupMetadata("test",
                    RevisionHasher.hashText("test"), CLOCK.instant(), true));
            return service;
        }
    }

    private static final class EmptyStageReferenceMigrationStore implements
            net.maddkraft.maddprestige.core.admin.config.StageReferenceMigrationStore {
        private Optional<net.maddkraft.maddprestige.core.stage.StageRemapPlan> lastPlan = Optional.empty();

        @Override
        public net.maddkraft.maddprestige.core.admin.config.StageReferenceSnapshot capture(
                Optional<net.maddkraft.maddprestige.core.stage.StageRemapPlan> plan) {
            lastPlan = plan;
            return new net.maddkraft.maddprestige.core.admin.config.StageReferenceSnapshot(Map.of(),
                    plan.map(value -> net.maddkraft.maddprestige.core.admin.config.StageRemapSnapshot.create(
                            value, List.of())));
        }

        @Override
        public net.maddkraft.maddprestige.core.admin.config.ConfigurationStageTransitionExecution beginTransition(
                ConfigRevisionId configurationRevision,
                Optional<ConfigRevisionId> priorRevision,
                net.maddkraft.maddprestige.core.config.ContentHash candidateHash,
                Map<net.maddkraft.maddprestige.api.id.StageId,
                        net.maddkraft.maddprestige.core.admin.config.ConfigurationStageReservationKind> reservedStages,
                Optional<net.maddkraft.maddprestige.core.admin.config.StageRemapSnapshot> snapshot,
                Actor actor,
                String reason,
                Instant occurredAt) {
            if (snapshot.isPresent()) {
                throw new AssertionError("An empty invalid remap must never execute");
            }
            return new net.maddkraft.maddprestige.core.admin.config.ConfigurationStageTransitionExecution(
                    configurationRevision, priorRevision, candidateHash, reservedStages, Optional.empty());
        }

        @Override
        public void markTransitionApplied(ConfigRevisionId configurationRevision, Instant occurredAt) {
        }

        @Override
        public void markTransitionFailedSafe(
                ConfigRevisionId configurationRevision,
                String detail,
                Instant occurredAt) {
        }

        @Override
        public void markTransitionNeedsReconciliation(
                ConfigRevisionId configurationRevision,
                String detail,
                Instant occurredAt) {
        }

        @Override
        public List<net.maddkraft.maddprestige.core.admin.config.ConfigurationStageTransitionState> transitions(
                int limit) {
            return List.of();
        }

        @Override
        public List<net.maddkraft.maddprestige.core.admin.config.StageRemapReconciliation> unresolved(int limit) {
            return List.of();
        }
    }

    private static String resource(String name) {
        try (var stream = PhaseSixCommandServiceTest.class.getResourceAsStream(name)) {
            if (stream == null) {
                throw new IllegalArgumentException("Missing test resource " + name);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static final class MemoryHistory implements ConfigurationHistoryStore {
        private final List<StoredConfigurationRevision> revisions = new ArrayList<>();

        @Override
        public void append(StoredConfigurationRevision revision) {
            revisions.add(revision);
        }

        @Override
        public void replaceOutcome(StoredConfigurationRevision revision) {
            for (int index = 0; index < revisions.size(); index++) {
                if (revisions.get(index).id().equals(revision.id())) {
                    revisions.set(index, revision);
                    return;
                }
            }
            throw new IllegalArgumentException("Unknown configuration revision " + revision.id().value());
        }

        @Override
        public Optional<StoredConfigurationRevision> find(ConfigRevisionId revisionId) {
            return revisions.stream().filter(revision -> revision.id().equals(revisionId)).findFirst();
        }

        @Override
        public List<StoredConfigurationRevision> recent(int limit) {
            return List.copyOf(revisions);
        }
    }

    private static final class MemorySnapshots implements ConfigurationSnapshotStore {
        @Override
        public PreparedConfigurationSnapshot prepare(
                ConfigRevisionId revisionId,
                CompiledConfiguration configuration) {
            return new PreparedConfigurationSnapshot() {
                @Override
                public BackupMetadata backup() {
                    return new BackupMetadata("test", RevisionHasher.hashText("test"), CLOCK.instant(), true);
                }

                @Override
                public void activate() {
                }

                @Override
                public void restorePrevious() {
                }

                @Override
                public void close() {
                }
            };
        }
    }
}
