package net.maddkraft.maddprestige.core.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.metric.MetricDescriptor;
import net.maddkraft.maddprestige.api.metric.MetricDimension;
import net.maddkraft.maddprestige.api.metric.MetricMonotonicity;
import net.maddkraft.maddprestige.api.metric.MetricOperator;
import net.maddkraft.maddprestige.api.metric.MetricProvider;
import net.maddkraft.maddprestige.api.metric.MetricQuery;
import net.maddkraft.maddprestige.api.metric.MetricReadMode;
import net.maddkraft.maddprestige.api.metric.MetricResetPolicy;
import net.maddkraft.maddprestige.api.metric.MetricSample;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.operation.Actor;
import net.maddkraft.maddprestige.api.provider.CapabilityDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderDescriptor;
import net.maddkraft.maddprestige.api.provider.ProviderHealth;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.rank.ManagedRankState;
import net.maddkraft.maddprestige.api.rank.RankAdapter;
import net.maddkraft.maddprestige.api.rank.RankProjectionRequest;
import net.maddkraft.maddprestige.api.rank.RankProjectionResult;
import net.maddkraft.maddprestige.api.result.Result;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.core.admin.command.CommandCompletionService;
import net.maddkraft.maddprestige.core.admin.command.ContextualHelpService;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationApplyKind;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationIntrospectionService;
import net.maddkraft.maddprestige.core.admin.diagnostic.DatabaseDiagnosticProbe;
import net.maddkraft.maddprestige.core.admin.diagnostic.DatabaseHealth;
import net.maddkraft.maddprestige.core.admin.diagnostic.DiagnosticSeverity;
import net.maddkraft.maddprestige.core.admin.diagnostic.DoctorService;
import net.maddkraft.maddprestige.core.admin.diagnostic.RankTargetDiagnosticProbe;
import net.maddkraft.maddprestige.core.admin.diagnostic.WhyService;
import net.maddkraft.maddprestige.core.config.ActiveConfiguration;
import net.maddkraft.maddprestige.core.config.CompiledConfiguration;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.core.plan.RankUpAuthorizationResult;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.core.rank.ReconciliationPolicy;
import net.maddkraft.maddprestige.core.schema.PhaseSixSchema;
import net.maddkraft.maddprestige.core.stage.StageConfiguration;
import net.maddkraft.maddprestige.core.stage.StageDefinition;
import net.maddkraft.maddprestige.core.stage.StageProjection;
import net.maddkraft.maddprestige.core.admin.ui.GuiActionKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhaseSixAdministrationUxTest {
    private static final Instant NOW = Instant.parse("2026-08-16T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("[A42] Search/explain derive paths, values, types, and consequences from the canonical schema")
    void introspectionUsesCanonicalSchemaAndActiveValue() {
        Map<String, String> documents = Map.of("lifecycle.yml", """
                schema-version: 4
                prestige:
                  enabled: false
                """);
        ActiveConfiguration active = new ActiveConfiguration(new ConfigRevisionId("explain_revision"),
                new CompiledConfiguration(RevisionHasher.hashDocuments(documents), documents));
        ConfigurationIntrospectionService service = new ConfigurationIntrospectionService(PhaseSixSchema.create(),
                () -> Optional.of(active), () -> ValidationReport.VALID);
        PermissionSubject viewer = subject(PhaseSixPermissions.CONFIG_VIEW);

        var explanation = service.explain(viewer, "prestige.enabled");

        assertEquals(Optional.of("false"), explanation.currentValue());
        assertEquals(MetricValueType.BOOLEAN.name(), explanation.type().name());
        assertTrue(explanation.description().contains("canonical Prestige planning"));
        assertTrue(service.search(viewer, "saved boundary", 20).stream().anyMatch(value ->
                value.canonicalPath().contains("measurement-scope")));
        assertThrows(AdministrationException.class, () -> service.explain(viewer, "../secrets"));
    }

    @Test
    @DisplayName("[A43] Completion is permission-aware, cached, bounded, and provider capability-driven")
    void completesOnlyAdvertisedProviderMetrics() {
        ProviderRegistry providers = new ProviderRegistry();
        var registration = providers.register("test-owner", new TestMetricProvider());
        providers.activate(registration);
        CommandCompletionService completion = new CommandCompletionService();
        completion.refresh(providers, PhaseSixSchema.create(), StageConfiguration.inactive());
        PermissionSubject setup = subject(PhaseSixPermissions.SETUP);

        assertEquals(List.of("metrics"), completion.suggest(setup,
                List.of("setup", "requirement", "session", "id", "")));
        assertEquals(List.of("blocks", "play_time"), completion.suggest(setup,
                List.of("setup", "requirement", "session", "id", "metrics", "")));
        assertEquals(List.of("since-prestige-start"), completion.suggest(setup,
                List.of("setup", "requirement", "session", "id", "metrics", "play_time", "greater-or-equal",
                        "10", "since-p")));
        assertTrue(completion.suggest(subject(PhaseSixPermissions.USE), List.of("")).stream()
                .noneMatch("requirement"::equals));

        PermissionSubject editor = subject(PhaseSixPermissions.CONFIG_EDIT);
        List<String> addPaths = completion.suggest(editor, List.of("config", "add", "draft", ""));
        List<String> removePaths = completion.suggest(editor, List.of("config", "remove", "draft", ""));
        assertTrue(addPaths.contains("progression.stages"));
        assertTrue(addPaths.contains("progression.order"));
        assertFalse(addPaths.contains("requirements.requirements"),
                "generic map mutation is not an executable command route");
        assertFalse(removePaths.contains("progression.stages"),
                "the map itself is not removable; only concrete stage paths are executable");
        assertTrue(removePaths.contains("progression.order"));

        completion.refreshAuthorities(Set.of(UUID.fromString("00000000-0000-0000-0000-000000000001")),
                Set.of("revision-one"));
        PermissionSubject rollback = subject(PhaseSixPermissions.CONFIG_ROLLBACK);
        assertTrue(completion.suggest(rollback, List.of("config", "")).containsAll(
                List.of("rollback", "rollback-apply", "remap", "validate", "diff", "cancel")));
        assertEquals(List.of(), completion.suggest(rollback,
                List.of("config", "acknowledge", "00000000-0000-0000-0000-000000000001", "")),
                "acknowledgement kind is server-owned and must not be suggested as caller input");
    }

    @Test
    @DisplayName("[A44] Measurement help uses canonical schema metadata in plain language")
    void explainsMeasurementPlainly() {
        var lines = new ContextualHelpService(PhaseSixSchema.create())
                .help(subject(PhaseSixPermissions.USE), "measurement");

        assertEquals(List.of("command.help.measurement.title", "command.help.measurement.description",
                "command.help.valid_values", "command.help.measurement.baseline_safety"),
                lines.stream().map(value -> value.key()).toList());
        assertTrue(lines.get(2).argument("value").orElseThrow().contains("since-stage-start"));
    }

    @Test
    @DisplayName("[A45] Healthy doctor is concise across configuration, provider, and database")
    void healthyDoctorReportsCoreStatus() {
        ProviderRegistry providers = new ProviderRegistry();
        var registration = providers.register("test-owner", new TestMetricProvider());
        providers.activate(registration);
        providers.register("test-owner", new TestRankAdapter(Set.of()));
        var active = new ActiveConfiguration(new ConfigRevisionId("healthy_revision"), compiled("ok: true\n"));
        var database = new DatabaseDiagnosticProbe(() -> CompletableFuture.completedFuture(
                new DatabaseHealth(true, true, "SQLite", "migration 6")));
        var operational = new net.maddkraft.maddprestige.core.admin.diagnostic.PhaseSixOperationalDiagnosticProbe(
                providers, () -> CompletableFuture.completedFuture(
                        new net.maddkraft.maddprestige.core.admin.diagnostic.PhaseSixOperationalSnapshot(
                                5, 5, List.of(), List.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                                Set.of(),
                                List.of(), List.of(), healthySubsystem("disabled and checked"),
                                healthySubsystem("scheduler and cache are current"),
                                healthySubsystem("flush queue is empty"), Map.of())));
        var rank = new RankTargetDiagnosticProbe(() -> Optional.of(StageConfiguration.inactive()), providers);
        var history = new net.maddkraft.maddprestige.core.admin.diagnostic.ConfigurationHistoryDiagnosticProbe(
                emptyHistory(), Runnable::run);
        DoctorService doctor = new DoctorService(providers, () -> Optional.of(active),
                List.of(database, operational, rank, history), CLOCK);

        var report = doctor.inspect(subject(PhaseSixPermissions.DOCTOR)).toCompletableFuture().join();

        assertEquals(DiagnosticSeverity.HEALTHY, report.status());
        assertTrue(report.findings().size() >= 10);
        assertTrue(report.findings().stream().anyMatch(value -> value.code().equals("config.active")));
        assertTrue(report.findings().stream().anyMatch(value -> value.code().equals("provider.available")));
        assertTrue(report.findings().stream().anyMatch(value -> value.component().equals("provider")
                && value.severity() == DiagnosticSeverity.DEFERRED));
        assertTrue(report.findings().stream().anyMatch(value -> value.code().equals("database.healthy")));
        assertTrue(report.findings().stream().noneMatch(value -> value.code().startsWith("doctor.not_checked")));
    }

    @Test
    @DisplayName("[A46] Doctor identifies the exact stage whose configured external group disappeared")
    void doctorFindsExactBrokenRankTarget() {
        ProviderRegistry providers = new ProviderRegistry();
        var registration = providers.register("test-owner", new TestRankAdapter(Set.of("member")));
        providers.activate(registration);
        StageConfiguration stages = stages("veteran");
        DoctorService doctor = new DoctorService(providers, Optional::<ActiveConfiguration>empty,
                List.of(new RankTargetDiagnosticProbe(() -> Optional.of(stages), providers)), CLOCK);

        var report = doctor.inspect(subject(PhaseSixPermissions.DOCTOR)).toCompletableFuture().join();

        assertEquals(DiagnosticSeverity.BLOCKED, report.status());
        assertTrue(report.findings().stream().anyMatch(value -> value.code().equals("rank.group.not_found")
                && value.path().equals("progression.stages.second.projection.group")
                && value.summary().contains("veteran")));
    }

    @Test
    @DisplayName("[A45][A46] Operational Doctor reports pending, provider, orphan, schema, and Placeholder failures")
    void doctorReportsExpandedOperationalFailuresAtExactPaths() {
        ProviderRegistry providers = new ProviderRegistry();
        UUID orphan = UUID.randomUUID();
        var probe = new net.maddkraft.maddprestige.core.admin.diagnostic.PhaseSixOperationalDiagnosticProbe(
                providers, () -> CompletableFuture.completedFuture(
                        new net.maddkraft.maddprestige.core.admin.diagnostic.PhaseSixOperationalSnapshot(
                                4, 5,
                                List.of(reference("missing_metric", Optional.of(new MetricId("play")),
                                        "requirements.play.metric")),
                                List.of(reference("missing_cost", Optional.empty(), "costs.payment.provider")),
                                List.of(reference("missing_reward", Optional.empty(), "rewards.grant.provider")),
                                Map.of("pending-1", "EXECUTING"), Map.of("remap-1", "MIGRATED_PENDING_CONFIG"),
                                Map.of("lease-1", new net.maddkraft.maddprestige.core.admin.diagnostic
                                        .DiagnosticSubsystemState(DiagnosticSeverity.BLOCKED,
                                                "Lease owner is missing")),
                                Map.of("revision-2", new net.maddkraft.maddprestige.core.admin.diagnostic
                                        .DiagnosticSubsystemState(DiagnosticSeverity.BLOCKED,
                                                "Configuration transition owner is incomplete")),
                                Map.of(orphan, "deleted"), Set.of("first"),
                                List.of(new net.maddkraft.maddprestige.core.admin.diagnostic.DiagnosticIntegrityIssue(
                                        "progression.stages.duplicate", "Duplicate immutable stage ID")),
                                List.of(new net.maddkraft.maddprestige.core.admin.diagnostic.DiagnosticIntegrityIssue(
                                        "entitlements.speed", "Entitlement contribution type mismatch")),
                                new net.maddkraft.maddprestige.core.admin.diagnostic.DiagnosticSubsystemState(
                                        DiagnosticSeverity.WARNING, "PlaceholderAPI expansion is unavailable"),
                                healthySubsystem("scheduler checked"), healthySubsystem("flush checked"),
                                Map.of("optional", new net.maddkraft.maddprestige.core.admin.diagnostic
                                        .DiagnosticSubsystemState(DiagnosticSeverity.DEFERRED,
                                                "optional capability is unsupported")))));

        List<net.maddkraft.maddprestige.core.admin.diagnostic.DiagnosticFinding> findings = probe.inspect()
                .toCompletableFuture().join();

        assertTrue(findings.stream().anyMatch(value -> value.code().equals("operation.pending")));
        assertTrue(findings.stream().anyMatch(value -> value.code().equals("requirement.metric.unavailable")
                && value.path().equals("requirements.play.metric")));
        assertTrue(findings.stream().anyMatch(value -> value.code().equals("cost.provider.unavailable")
                && value.path().equals("costs.payment.provider")));
        assertTrue(findings.stream().anyMatch(value -> value.code().equals("reward.provider.unavailable")
                && value.path().equals("rewards.grant.provider")));
        assertTrue(findings.stream().anyMatch(value -> value.code().equals("player.stage.orphaned")
                && value.path().equals("players." + orphan + ".stage")));
        assertTrue(findings.stream().anyMatch(value -> value.code().equals("config.schema_version_mismatch")));
        assertTrue(findings.stream().anyMatch(value -> value.path().equals("configuration.transitions.revision-2")
                && value.severity() == DiagnosticSeverity.BLOCKED));
        assertTrue(findings.stream().anyMatch(value -> value.path().equals("integrations.placeholderapi")
                && value.severity() == DiagnosticSeverity.WARNING));
    }

    @Test
    @DisplayName("[A45][A69] Doctor distinguishes active transition participation from abnormal lease ownership")
    void doctorClassifiesTransitionLeaseDiagnosticsWithoutFatalizingHealthyActivity() {
        var active = new net.maddkraft.maddprestige.core.stage.StageTransitionLease(OperationId.random(),
                Optional.of(new StageId("source")), new StageId("target"),
                new ConfigRevisionId("lease_revision"), Optional.of(
                        net.maddkraft.maddprestige.api.operation.OperationState.EXECUTING), true, CLOCK.instant());
        var abnormal = new net.maddkraft.maddprestige.core.stage.StageTransitionLease(OperationId.random(),
                Optional.empty(), new StageId("legacy_target"), new ConfigRevisionId("lease_revision"),
                Optional.empty(), false, CLOCK.instant());

        var states = net.maddkraft.maddprestige.core.admin.diagnostic.PhaseSixOperationalSnapshot
                .diagnoseLeases(List.of(active, abnormal));

        assertEquals(DiagnosticSeverity.WARNING, states.get(active.operationId().toString()).severity());
        assertEquals(DiagnosticSeverity.BLOCKED, states.get(abnormal.operationId().toString()).severity());
    }

    @Test
    @DisplayName("[A45][A69] Doctor reports normal and incomplete configuration-transition authority distinctly")
    void doctorClassifiesConfigurationTransitionRecoveryState() {
        ConfigRevisionId revision = new ConfigRevisionId("candidate_revision");
        var healthy = new net.maddkraft.maddprestige.core.admin.config.ConfigurationStageTransitionState(
                revision, Optional.of(new ConfigRevisionId("prior_revision")),
                net.maddkraft.maddprestige.core.config.RevisionHasher.hashText("candidate"),
                net.maddkraft.maddprestige.core.admin.config.ConfigurationStageTransitionStatus.RESERVED, true,
                Map.of(new StageId("d"), net.maddkraft.maddprestige.core.admin.config
                        .ConfigurationStageReservationKind.REMOVED),
                Optional.of(net.maddkraft.maddprestige.core.admin.config.ConfigurationApplicationStatus.ATTEMPTED),
                CLOCK.instant(), "activation pending");
        var incomplete = new net.maddkraft.maddprestige.core.admin.config.ConfigurationStageTransitionState(
                new ConfigRevisionId("legacy_revision"), Optional.empty(),
                net.maddkraft.maddprestige.core.config.RevisionHasher.hashText("legacy"),
                net.maddkraft.maddprestige.core.admin.config.ConfigurationStageTransitionStatus
                        .NEEDS_RECONCILIATION,
                false, Map.of(new StageId("unknown"), net.maddkraft.maddprestige.core.admin.config
                        .ConfigurationStageReservationKind.UNKNOWN_LEGACY), Optional.empty(), CLOCK.instant(),
                "scope unknown");

        var states = net.maddkraft.maddprestige.core.admin.diagnostic.PhaseSixOperationalSnapshot
                .diagnoseTransitions(List.of(healthy, incomplete));

        assertEquals(DiagnosticSeverity.WARNING, states.get(revision.value()).severity());
        assertEquals(DiagnosticSeverity.BLOCKED, states.get("legacy_revision").severity());
        assertTrue(states.get("legacy_revision").detail().contains("UNKNOWN_LEGACY"));
    }

    @Test
    @DisplayName("[A45] Dormant optional provider remains nonfatal and is described as dormant")
    void dormantProviderRemainsNonfatal() {
        ProviderRegistry providers = new ProviderRegistry();
        providers.register("test-owner", new TestMetricProvider());
        DoctorService doctor = new DoctorService(providers, Optional::<ActiveConfiguration>empty, List.of(), CLOCK);

        var report = doctor.inspect(subject(PhaseSixPermissions.DOCTOR)).toCompletableFuture().join();

        assertTrue(report.status() != DiagnosticSeverity.BLOCKED);
        assertTrue(report.findings().stream().anyMatch(value -> value.component().equals("provider")
                && value.severity() == DiagnosticSeverity.DEFERRED
                && value.remediation().contains("dormant provider")));
    }

    @Test
    @DisplayName("[A47] Why returns exact canonical blocker text without simplified recomputation")
    void whyUsesAuthorizationBoundary() {
        String blocker = "stage second: requirement play 4/10; cost vault 20/25; provider metrics unavailable";
        WhyService why = new WhyService(intent -> CompletableFuture.completedFuture(
                RankUpAuthorizationResult.rejected(blocker)), intent -> CompletableFuture.completedFuture(
                        net.maddkraft.maddprestige.core.prestige.PrestigeAuthorizationResult
                                .rejected("Prestige unavailable")));
        UUID player = UUID.randomUUID();
        PermissionSubject self = new PermissionSubject(new Actor("player", Optional.of(player), "Player"),
                Set.of(PhaseSixPermissions.USE));

        var report = why.rankUp(self, player).toCompletableFuture().join();

        assertFalse(report.executable());
        assertEquals(List.of(blocker), report.blockers());
    }

    @Test
    @DisplayName("[A56][A69] GUI actions are permission-scoped, server-owned, stale-safe, and require remap")
    void guiGuardsAuthorityStalenessAndStageReplacement() {
        AtomicReference<Optional<ConfigRevisionId>> revision = new AtomicReference<>(
                Optional.of(new ConfigRevisionId("revision_one")));
        AtomicInteger executions = new AtomicInteger();
        net.maddkraft.maddprestige.core.admin.ui.GuiSessionService sessions =
                new net.maddkraft.maddprestige.core.admin.ui.GuiSessionService(revision::get,
                        (subject, action) -> {
                            executions.incrementAndGet();
                            return CompletableFuture.completedFuture(
                                    net.maddkraft.maddprestige.core.admin.presentation.MessageReference.of(
                                            "gui.result.configuration_inactive"));
                        }, new net.maddkraft.maddprestige.core.admin.ui.GuiConfigurationAuthority() {
                            @Override
                            public ConfigurationApplyKind draftKind(PermissionSubject subject, UUID draftId) {
                                return ConfigurationApplyKind.NORMAL;
                            }

                            @Override
                            public ConfigurationApplyKind acknowledgementKind(
                                    PermissionSubject subject,
                                    UUID acknowledgementId) {
                                return ConfigurationApplyKind.NORMAL;
                            }
                        }, Duration.ofMinutes(5), CLOCK);
        PermissionSubject viewer = subject(PhaseSixPermissions.ADMIN_GUI, PhaseSixPermissions.CONFIG_VIEW);
        var staff = sessions.openStaff(viewer);
        assertTrue(staff.actions().stream().anyMatch(value -> value.kind() == GuiActionKind.VIEW_CONFIGURATION));
        assertTrue(staff.actions().stream().noneMatch(value -> value.kind() == GuiActionKind.APPLY_CONFIGURATION));
        assertThrows(AdministrationException.class, () -> sessions.click(
                viewer, staff.sessionId(), UUID.randomUUID()));

        PermissionSubject editor = subject(PhaseSixPermissions.ADMIN_GUI, PhaseSixPermissions.CONFIG_EDIT);
        UUID draft = UUID.randomUUID();
        var blockedDelete = sessions.openStageEditor(editor, draft, new StageId("second"), Optional.empty());
        AdministrationException remap = assertThrows(AdministrationException.class, () -> sessions.click(editor,
                blockedDelete.sessionId(), blockedDelete.actions().getFirst().actionId()));
        assertEquals("stage.change.remap_required", remap.code());
        assertEquals(0, executions.get());

        var staleDelete = sessions.openStageEditor(editor, draft, new StageId("second"),
                Optional.of(new StageId("first")));
        revision.set(Optional.of(new ConfigRevisionId("revision_two")));
        AdministrationException stale = assertThrows(AdministrationException.class, () -> sessions.click(editor,
                staleDelete.sessionId(), staleDelete.actions().getFirst().actionId()));
        assertEquals("gui.action.stale", stale.code());
        assertEquals(0, executions.get());
    }

    private static net.maddkraft.maddprestige.core.admin.diagnostic.DiagnosticSubsystemState healthySubsystem(
            String detail) {
        return net.maddkraft.maddprestige.core.admin.diagnostic.DiagnosticSubsystemState.healthy(detail);
    }

    private static net.maddkraft.maddprestige.core.admin.diagnostic.DiagnosticProviderReference reference(
            String provider,
            Optional<MetricId> metric,
            String path) {
        return new net.maddkraft.maddprestige.core.admin.diagnostic.DiagnosticProviderReference(
                new ProviderId(provider), metric, path);
    }

    private static net.maddkraft.maddprestige.core.admin.config.ConfigurationHistoryStore emptyHistory() {
        return new net.maddkraft.maddprestige.core.admin.config.ConfigurationHistoryStore() {
            @Override
            public void append(net.maddkraft.maddprestige.core.admin.config.StoredConfigurationRevision revision) {
            }

            @Override
            public void replaceOutcome(
                    net.maddkraft.maddprestige.core.admin.config.StoredConfigurationRevision revision) {
            }

            @Override
            public Optional<net.maddkraft.maddprestige.core.admin.config.StoredConfigurationRevision> find(
                    ConfigRevisionId revisionId) {
                return Optional.empty();
            }

            @Override
            public List<net.maddkraft.maddprestige.core.admin.config.StoredConfigurationRevision> recent(int limit) {
                return List.of();
            }
        };
    }

    private static PermissionSubject subject(String... permissions) {
        return new PermissionSubject(new Actor("console", Optional.empty(), "Console"), Set.of(permissions));
    }

    private static CompiledConfiguration compiled(String source) {
        Map<String, String> documents = Map.of("config.yml", source);
        return new CompiledConfiguration(RevisionHasher.hashDocuments(documents), documents);
    }

    private static StageConfiguration stages(String group) {
        StageId firstId = new StageId("first");
        StageId secondId = new StageId("second");
        StageDefinition first = new StageDefinition(firstId, true, "First", Map.of(), StageProjection.none());
        StageDefinition second = new StageDefinition(secondId, true, "Second", Map.of(),
                StageProjection.group(new ProviderId("rank"), group));
        return new StageConfiguration(3, true, Map.of(firstId, first, secondId, second),
                List.of(firstId, secondId), Optional.of(firstId), ReconciliationPolicy.WARN_ONLY);
    }

    private static ProviderDescriptor descriptor(String id, String capability) {
        return new ProviderDescriptor(new ProviderId(id), "test-owner", "1", "1", List.of(),
                List.of(new CapabilityDescriptor(capability, capability, "test", Map.of())));
    }

    private static ProviderHealth health() {
        return new ProviderHealth(ProviderHealthState.AVAILABLE, "available", "ready", NOW);
    }

    private static final class TestMetricProvider implements MetricProvider {
        @Override
        public ProviderDescriptor descriptor() {
            return PhaseSixAdministrationUxTest.descriptor("metrics", "metric");
        }

        @Override
        public ProviderHealth health() {
            return PhaseSixAdministrationUxTest.health();
        }

        @Override
        public Set<MetricDescriptor> metrics() {
            return Set.of(metric("play_time"), metric("blocks"));
        }

        @Override
        public java.util.concurrent.CompletionStage<Map<MetricQuery, MetricSample>> read(
                UUID playerId, List<MetricQuery> queries, long providerGeneration) {
            return CompletableFuture.completedFuture(Map.of());
        }

        private static MetricDescriptor metric(String id) {
            return new MetricDescriptor(new ProviderId("metrics"), new MetricId(id), MetricValueType.COUNT,
                    Set.of(MetricOperator.GREATER_OR_EQUAL), Set.of(MetricReadMode.CURRENT), true,
                    MetricMonotonicity.MONOTONIC, MetricResetPolicy.NOT_APPLICABLE,
                    Map.<String, MetricDimension>of(), id, id, "count", "authoritative");
        }
    }

    private static final class TestRankAdapter implements RankAdapter {
        private final Set<String> groups;

        private TestRankAdapter(Set<String> groups) {
            this.groups = groups;
        }

        @Override
        public ProviderDescriptor descriptor() {
            return PhaseSixAdministrationUxTest.descriptor("rank", "rank");
        }

        @Override
        public ProviderHealth health() {
            return PhaseSixAdministrationUxTest.health();
        }

        @Override
        public java.util.concurrent.CompletionStage<Result<Set<String>>> validateTargets(Set<String> groupNames) {
            return CompletableFuture.completedFuture(Result.success(groups));
        }

        @Override
        public java.util.concurrent.CompletionStage<Result<ManagedRankState>> readManagedState(
                UUID playerId, Set<String> managedGroups) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.concurrent.CompletionStage<Result<RankProjectionResult>> project(
                RankProjectionRequest request) {
            throw new UnsupportedOperationException();
        }
    }
}
