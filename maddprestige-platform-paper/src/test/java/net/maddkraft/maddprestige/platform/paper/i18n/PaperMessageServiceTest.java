package net.maddkraft.maddprestige.platform.paper.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PaperMessageServiceTest {
    @TempDir
    Path temporaryDirectory;

    private final List<String> diagnostics = new ArrayList<>();
    private PaperMessageService messages;

    @BeforeEach
    void open() throws Exception {
        messages = PaperMessageService.open(temporaryDirectory,
                PaperMessageService.class.getClassLoader(), diagnostics::add);
    }

    @Test
    @DisplayName("[A68][D-F06][D-F07] Selected locale falls back to complete en_US then a bounded key")
    void deterministicFallbacks() throws Exception {
        select("zz_ZZ", "command.failed: \"Unicode: 你好 — Zażółć\"\n");
        assertTrue(messages.reload().successful());

        assertEquals("Unicode: 你好 — Zażółć", plain(messages.render("command.failed")));
        assertEquals("MaddPrestige V2 configuration: revision-7",
                plain(messages.render("command.status.configuration", Map.of("revision", "revision-7"))));
        assertEquals("[message:missing.public.key]", plain(messages.render("missing.public.key")));
        assertTrue(diagnostics.stream().anyMatch(value -> value.contains("missing.public.key")));
    }

    @Test
    @DisplayName("[A68][D-F08] Malformed catalog reload is rejected without replacing known-good state")
    void rejectsMalformedReloadAtomically() throws Exception {
        select("zz_ZZ", "command.failed: \"Known good\"\n");
        assertTrue(messages.reload().successful());
        long version = messages.version();
        assertEquals("Known good", plain(messages.render("command.failed")));

        writeCatalog("zz_ZZ", "command.failed: \"<red>unclosed\"\n");
        LocaleReloadResult rejected = messages.reload();

        assertFalse(rejected.successful());
        assertEquals(version, messages.version());
        assertEquals("Known good", plain(messages.render("command.failed")));
    }

    @Test
    @DisplayName("[A68][D-F09] Dynamic MiniMessage, nested placeholders and controls remain bounded untrusted data")
    void dynamicArgumentsCannotGainFormattingAuthority() {
        String attacker = "<click:run_command:'/op me'><red><value>legacy\u0000name</red></click>";
        String rendered = plain(messages.render("command.config.list_value", Map.of("value", attacker)));

        assertEquals("<click:run_command:'/op me'><red><value>legacy�name</red></click>",
                rendered);
        assertTrue(rendered.contains("<value>"));
    }

    @Test
    @DisplayName("[A68][D-F10] Concurrent rendering observes only complete old or new catalogs")
    void concurrentReloadNeverPublishesPartialCatalog() throws Exception {
        select("zz_ZZ", "command.failed: \"old\"\ncommand.status.configuration: \"old <revision>\"\n");
        assertTrue(messages.reload().successful());
        ConcurrentLinkedQueue<String> observed = new ConcurrentLinkedQueue<>();

        try (var workers = Executors.newFixedThreadPool(6)) {
            var renders = java.util.stream.IntStream.range(0, 6).mapToObj(index -> workers.submit(() -> {
                for (int count = 0; count < 2_000; count++) {
                    observed.add(plain(messages.render("command.status.configuration", Map.of("revision", "value"))));
                }
            })).toList();
            writeCatalog("zz_ZZ", "command.failed: \"new\"\ncommand.status.configuration: \"new <revision>\"\n");
            assertTrue(messages.reload().successful());
            for (var render : renders) {
                render.get();
            }
        }

        assertFalse(observed.isEmpty());
        assertTrue(observed.stream().allMatch(value -> value.equals("old value") || value.equals("new value")));
        assertEquals("new value", plain(messages.render("command.status.configuration", Map.of("revision", "value"))));
    }

    @Test
    @DisplayName("[A68] Atomic successful reload changes locale and publishes a complete new version")
    void successfulReloadPublishesNewVersion() throws Exception {
        long previous = messages.version();
        select("zz_ZZ", "locale.reload.success: \"Gotowe <locale>\"\n");

        LocaleReloadResult result = messages.reload();

        assertTrue(result.successful());
        assertEquals("zz_ZZ", messages.locale());
        assertTrue(messages.version() > previous);
        assertEquals("Gotowe zz_ZZ", plain(messages.render("locale.reload.success", Map.of(
                "locale", "zz_ZZ"))));
    }

    @Test
    @DisplayName("[A68] Built-in en_US covers every required Paper presentation key")
    void builtInCatalogHasCompleteCoverage() {
        for (String key : messages.requiredKeys()) {
            String rendered = plain(messages.render(key, arguments()));
            if (rendered.startsWith("[message:")) {
                rendered = plain(messages.render(key, Map.of(
                        "code", "value", "document", "value", "errors", "value", "findings", "value",
                        "purpose", "value", "source", "value", "stage", "value", "target", "value",
                        "before", "value", "after", "value")));
            }
            assertFalse(rendered.startsWith("[message:"), key);
        }
    }

    @Test
    @DisplayName("[A68][OR8D-01] Command, GUI, and Phase 7 semantic prose is selected by catalog key")
    void selectedCatalogControlsRepresentativeSemanticPresentation() throws Exception {
        select("zz_ZZ", """
                command.status.configuration: "KOMENDA <revision>"
                gui.title.progress: "GUI POSTEP"
                phase7.usage: "FAZA SIEDEM"
                """);
        assertTrue(messages.reload().successful());

        assertEquals("KOMENDA r9", plain(messages.render(
                net.maddkraft.maddprestige.core.admin.presentation.MessageReference.of(
                        "command.status.configuration", "revision", "r9"))));
        var response = net.maddkraft.maddprestige.core.admin.command.CommandResponse.success("status", List.of(
                net.maddkraft.maddprestige.core.admin.presentation.MessageReference.of(
                        "command.status.configuration", "revision", "r9")));
        assertEquals("KOMENDA r9", plain(
                net.maddkraft.maddprestige.platform.paper.admin.PaperPhaseSixCommandAdapter
                        .renderResponse(response, messages).getFirst()));
        assertEquals("GUI POSTEP", plain(messages.render(
                net.maddkraft.maddprestige.core.admin.presentation.MessageReference.of("gui.title.progress"))));
        var view = new net.maddkraft.maddprestige.core.admin.ui.GuiSessionView(java.util.UUID.randomUUID(),
                net.maddkraft.maddprestige.core.admin.ui.GuiAudience.PLAYER,
                net.maddkraft.maddprestige.core.admin.presentation.MessageReference.of("gui.title.progress"),
                List.of(), java.time.Instant.parse("2026-08-18T12:00:00Z"));
        assertEquals("GUI POSTEP", plain(
                net.maddkraft.maddprestige.platform.paper.admin.PaperGuiInventory.renderTitle(view, messages)));
        assertEquals("FAZA SIEDEM", plain(messages.render(
                net.maddkraft.maddprestige.core.admin.presentation.MessageReference.of("phase7.usage"))));
    }

    @Test
    @DisplayName("[A47][A68][OR8D-07] Why renders requirement and non-requirement blocker identity")
    void semanticWhyRendersEveryBlockerIdentity() {
        var requirement = new net.maddkraft.maddprestige.api.explanation.ExplanationNode(
                "requirement.unsatisfied",
                net.maddkraft.maddprestige.api.explanation.ExplanationStatus.UNSATISFIED,
                "stored English must not render",
                Map.of("requirement", "playtime", "metric", "play_time", "current", "30", "target", "60"),
                List.of());
        var requirementBlocker = blocker(
                net.maddkraft.maddprestige.core.authorization.AuthorizationBlockerKind.REQUIREMENT_UNSATISFIED,
                "Requirement tree is UNSATISFIED", "requirement", "playtime", "status", "UNSATISFIED");
        var providerBlocker = blocker(
                net.maddkraft.maddprestige.core.authorization.AuthorizationBlockerKind.REQUIRED_PROVIDER_UNAVAILABLE,
                "Provider is unavailable or stale: vault", "provider", "vault");
        var blockers = List.of(requirementBlocker, providerBlocker);
        var report = new net.maddkraft.maddprestige.core.admin.diagnostic.WhyReport(false,
                net.maddkraft.maddprestige.core.authorization.AuthorizationBlocker.diagnostics(blockers),
                java.util.Optional.of(requirement),
                java.util.Optional.of(new net.maddkraft.maddprestige.api.id.ConfigRevisionId("r-why")), blockers);

        List<String> rendered = render(net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation
                .why(report));

        assertTrue(rendered.stream().anyMatch(value -> value.contains("playtime")
                && value.contains("UNSATISFIED")));
        assertTrue(rendered.stream().anyMatch(value -> value.contains("vault") && value.contains("absent")));
        assertTrue(rendered.stream().anyMatch(value -> value.contains("playtime")
                && value.contains("30") && value.contains("60")));
        assertTrue(rendered.stream().anyMatch(value -> value.contains("r-why")));
        assertFalse(rendered.stream().anyMatch(value -> value.contains("stored English")));

        for (var kind : net.maddkraft.maddprestige.core.authorization.AuthorizationBlockerKind.values()) {
            var reference = net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation
                    .authorizationBlocker(new net.maddkraft.maddprestige.core.authorization.AuthorizationBlocker(
                            kind, blockerFacts(), "INTERNAL_ENGLISH_SENTINEL"));
            assertEquals("command.why.blocker." + kind.catalogIdentity(), reference.key());
            assertFalse(reference.arguments().containsValue("INTERNAL_ENGLISH_SENTINEL"));
            assertFalse(plain(messages.render(reference)).startsWith("[message:"), kind.name());
        }

        String maximum = plain(messages.render(net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation
                .authorizationBlocker(new net.maddkraft.maddprestige.core.authorization.AuthorizationBlocker(
                        net.maddkraft.maddprestige.core.authorization.AuthorizationBlockerKind
                                .PRESTIGE_MAXIMUM_REACHED,
                        blockerFacts(), "maximum diagnostic"))));
        String inactive = plain(messages.render(net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation
                .authorizationBlocker(new net.maddkraft.maddprestige.core.authorization.AuthorizationBlocker(
                        net.maddkraft.maddprestige.core.authorization.AuthorizationBlockerKind.STAGE_LADDER_INACTIVE,
                        blockerFacts(), "inactive diagnostic"))));
        assertFalse(maximum.equals(inactive));
    }

    @Test
    @DisplayName("[A45][A46][A68][OR8D-07] Doctor, validation, admin, and config explanations stay actionable")
    void restoredDiagnosticSurfacesRenderCatalogOwnedMeaningAndRemediation() {
        var doctor = new net.maddkraft.maddprestige.core.admin.diagnostic.DiagnosticFinding(
                "database.unreachable",
                net.maddkraft.maddprestige.core.admin.diagnostic.DiagnosticSeverity.BLOCKED,
                "database", "database.connection", "DOCTOR_SENTINEL", "DOCTOR_REMEDIATION_SENTINEL");
        List<String> doctorOutput = render(net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation
                .doctorFinding(doctor));
        assertTrue(doctorOutput.stream().anyMatch(value -> value.contains("database.connection")
                && value.contains("durable state")));
        assertTrue(doctorOutput.stream().map(value -> value.toLowerCase(java.util.Locale.ROOT))
                .anyMatch(value -> value.contains("restore") && value.contains("migration")));

        var validation = new net.maddkraft.maddprestige.api.validation.ValidationFinding(
                "phase3.provider.unavailable",
                net.maddkraft.maddprestige.api.validation.ValidationSeverity.ERROR,
                "providers.vault", "VALIDATION_SENTINEL", "CONSEQUENCE_SENTINEL", "REMEDIATION_SENTINEL");
        List<String> validationOutput = render(
                net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation
                        .validationFinding(validation));
        assertTrue(validationOutput.stream().anyMatch(value -> value.contains("providers.vault")
                && value.contains("provider capability")));
        assertTrue(validationOutput.stream().anyMatch(value -> value.contains("restore")
                || value.contains("Restore")));

        var administration = new net.maddkraft.maddprestige.core.admin.AdministrationException(
                "config.revision.stale", "ADMIN_SENTINEL", "ADMIN_REMEDIATION_SENTINEL");
        List<String> administrationOutput = render(
                net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation
                        .administration(administration));
        assertTrue(administrationOutput.stream().anyMatch(value -> value.contains("changed")
                && value.contains("config.revision.stale")));
        assertTrue(administrationOutput.stream().anyMatch(value -> value.contains("preview")
                && value.contains("fresh authority")));

        var explanation = new net.maddkraft.maddprestige.core.admin.config.ConfigurationExplanation(
                "prestige.enabled", "prestige_enabled",
                net.maddkraft.maddprestige.core.schema.SchemaValueType.BOOLEAN,
                java.util.Optional.of("false"), java.util.Optional.of("false"), "CONFIG_SENTINEL",
                List.of("true", "false"), List.of(), net.maddkraft.maddprestige.core.schema.RiskLevel.CRITICAL,
                net.maddkraft.maddprestige.core.schema.ReloadBehavior.HOT_RELOAD,
                "maddprestige.admin.config.edit", "maddprestige.admin.config.apply");
        assertEquals("Enables canonical Prestige planning and execution.", plain(messages.render(
                net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation
                        .configurationDescription(explanation))));

        assertFalse(java.util.stream.Stream.of(doctorOutput, validationOutput, administrationOutput)
                .flatMap(List::stream).anyMatch(value -> value.contains("SENTINEL")));
    }

    @Test
    @DisplayName("[A68][OR8D-09] Exact administration codes retain their own facts and remediation")
    void exactAdministrationSemanticsAreNotGuessedFromCodeFragments() {
        var baseline = new net.maddkraft.maddprestige.core.admin.AdministrationException(
                "setup.baseline.unknown", "INTERNAL", "INTERNAL", "stage", "member");
        var path = new net.maddkraft.maddprestige.core.admin.AdministrationException(
                "config.path.unknown", "INTERNAL", "INTERNAL", "path", "prestige.missing");
        var duplicate = new net.maddkraft.maddprestige.core.admin.AdministrationException(
                "setup.stage.duplicate", "INTERNAL", "INTERNAL", "stage", "adventurer");

        List<String> baselineLines = render(
                net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation.administration(baseline));
        List<String> pathLines = render(
                net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation.administration(path));
        List<String> duplicateLines = render(
                net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation.administration(duplicate));

        assertTrue(baselineLines.stream().anyMatch(value -> value.contains("member")
                && value.contains("wizard stages")));
        assertFalse(baselineLines.stream().anyMatch(value -> value.contains("draft, session, revision")));
        assertTrue(pathLines.stream().anyMatch(value -> value.contains("prestige.missing")
                && value.contains("configuration path")));
        assertFalse(pathLines.stream().anyMatch(value -> value.contains("server-owned draft")));
        assertTrue(duplicateLines.stream().anyMatch(value -> value.contains("adventurer")));
        assertTrue(duplicateLines.stream().anyMatch(value -> value.contains("unique immutable stage ID")));
    }

    @Test
    @DisplayName("[A68][OR8D-09] Every known public AdministrationException code has an exact mapping")
    void administrationMappingIsComplete() {
        Set<String> expected = Set.of(
                "command.player_required", "config.acknowledgement.actor_mismatch",
                "config.acknowledgement.already_used", "config.acknowledgement.expired",
                "config.acknowledgement.findings_changed", "config.acknowledgement.not_required",
                "config.acknowledgement.server_authority_required", "config.acknowledgement.stale",
                "config.acknowledgement.unknown", "config.active.absent", "config.add.rejected",
                "config.apply.failed", "config.apply.kind_mismatch", "config.document.missing",
                "config.draft.apply_in_progress", "config.draft.cancelled", "config.draft.changed_during_apply",
                "config.draft.changed_during_prepare", "config.draft.concurrent_change",
                "config.draft.concurrent_edit", "config.draft.expired", "config.draft.owner_mismatch",
                "config.draft.unknown", "config.edit.rejected", "config.history.finalize_failed",
                "config.list.rejected", "config.path.not_editable", "config.path.not_listable",
                "config.path.type_mismatch", "config.path.unknown", "config.preview.candidate_missing",
                "config.preview.required", "config.preview.stale", "config.remove.rejected",
                "config.revision.stale", "config.rollback.not_applied", "config.rollback.not_prepared",
                "config.rollback.unknown", "config.snapshot.activate_failed", "config.snapshot.prepare_failed",
                "config.validation.blocked", "config.value.not_allowed", "confirmation.actor_mismatch",
                "confirmation.already_used", "confirmation.config_stale", "confirmation.expired",
                "confirmation.unknown", "gui.action.forged", "gui.action.stale",
                "gui.mutation.context_missing", "gui.mutation.kind_invalid", "gui.session.actor_mismatch",
                "gui.session.expired", "gui.target.missing", "operation.preview.blocked", "permission.denied",
                "setup.acknowledgement.unknown", "setup.already_active", "setup.baseline.unknown",
                "setup.draft.invalid", "setup.group.missing", "setup.incomplete",
                "setup.integration.unconfigurable",
                "setup.prestige.stage_unknown", "setup.preview.required", "setup.provider.required",
                "setup.requirement.baseline", "setup.requirement.completion.invalid",
                "setup.requirement.duplicate", "setup.requirement.metric_unknown",
                "setup.requirement.operator.invalid", "setup.requirement.scope.invalid",
                "setup.requirement.target.invalid", "setup.session.owner_mismatch", "setup.session.unknown",
                "setup.stage.duplicate", "setup.text.control_character", "stage.add.rejected",
                "stage.change.remap_candidate_invalid", "stage.change.remap_invalid",
                "stage.change.remap_missing", "stage.change.remap_required",
                "stage.change.remap_snapshot_stale", "stage.change.remap_source_missing",
                "stage.change.remap_source_present", "stage.change.remap_target_missing",
                "stage.change.transition_reconciliation_pending", "stage.change.transition_stale",
                "stage.remove.rejected");
        assertEquals(expected,
                net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation
                        .knownAdministrationCodes());
    }

    @Test
    @DisplayName("[A68][OR8D-09] Every administration code has a deliberate dedicated semantic identity")
    void administrationSemanticIdentityAuditHasNoCoarseFamilies() {
        Map<String, String> identities = net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation
                .administrationSemanticIdentities();

        assertEquals(89, identities.size());
        assertEquals(89, new java.util.HashSet<>(identities.values()).size(),
                "each reviewed code owns one exact semantic identity");
        assertTrue(java.util.Collections.disjoint(new java.util.HashSet<>(identities.values()), Set.of(
                "permission", "expired", "authority", "stale", "configuration", "missing", "invalid",
                "recovery", "target", "general")));
        assertEquals("config_acknowledgement_server_authority_required",
                identities.get("config.acknowledgement.server_authority_required"));
        assertEquals("config_acknowledgement_not_required",
                identities.get("config.acknowledgement.not_required"));
        assertEquals("setup_group_missing", identities.get("setup.group.missing"));
        assertEquals("setup_integration_unconfigurable", identities.get("setup.integration.unconfigurable"));
        assertEquals("setup_requirement_duplicate", identities.get("setup.requirement.duplicate"));
        assertEquals("setup_prestige_stage_unknown", identities.get("setup.prestige.stage_unknown"));
        assertEquals("setup_requirement_metric_unknown", identities.get("setup.requirement.metric_unknown"));
        assertEquals("config_document_missing", identities.get("config.document.missing"));
        identities.forEach((code, identity) -> {
            assertTrue(messages.requiredKeys().contains(
                    "command.error.administration." + identity + ".summary"), code);
            assertTrue(messages.requiredKeys().contains(
                    "command.error.administration." + identity + ".remediation"), code);
            List<net.maddkraft.maddprestige.core.admin.presentation.MessageReference> references =
                    net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation.administration(
                            new net.maddkraft.maddprestige.core.admin.AdministrationException(
                                    code, "INTERNAL", "INTERNAL"));
            assertEquals("command.error.administration." + identity + ".summary",
                    references.getFirst().key(), code);
            assertEquals("command.error.administration." + identity + ".remediation",
                    references.getLast().key(), code);
        });
    }

    @Test
    @DisplayName("[A68][OR8D-09] Multi-throw inventory matches the reviewed compatibility registry")
    void administrationMultiThrowInventoryMatchesReviewedCompatibilityRegistry() throws Exception {
        Path root = repositoryRoot();
        Pattern constructor = Pattern.compile(
                "new\\s+AdministrationException\\s*\\(\\s*\"([^\"]+)\"");
        Map<String, Integer> counts = new java.util.TreeMap<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java"))
                    .filter(value -> !value.toString().replace('\\', '/').contains("/target/"))
                    .filter(value -> value.toString().replace('\\', '/').contains("/src/main/java/"))
                    .toList()) {
                var matcher = constructor.matcher(Files.readString(path, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    counts.merge(matcher.group(1), 1, Integer::sum);
                }
            }
        }
        assertEquals(net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation
                .knownAdministrationCodes(), counts.keySet(), "literal production code inventory");
        counts.entrySet().removeIf(entry -> entry.getValue() < 2);
        Map<String, Integer> expected = Map.ofEntries(
                Map.entry("config.acknowledgement.actor_mismatch", 2),
                Map.entry("config.acknowledgement.already_used", 2),
                Map.entry("config.acknowledgement.unknown", 2),
                Map.entry("config.active.absent", 3),
                Map.entry("config.apply.failed", 2),
                Map.entry("config.apply.kind_mismatch", 3),
                Map.entry("config.document.missing", 4),
                Map.entry("config.draft.apply_in_progress", 2),
                Map.entry("config.draft.cancelled", 2),
                Map.entry("config.draft.changed_during_apply", 2),
                Map.entry("config.draft.concurrent_edit", 4),
                Map.entry("config.path.not_listable", 2),
                Map.entry("config.path.unknown", 4),
                Map.entry("config.preview.required", 2),
                Map.entry("config.preview.stale", 2),
                Map.entry("config.revision.stale", 2),
                Map.entry("config.validation.blocked", 2),
                Map.entry("setup.preview.required", 2),
                Map.entry("stage.change.remap_invalid", 2));
        assertEquals(expected, counts);

        Set<String> compatible = Set.of(
                "config.acknowledgement.actor_mismatch", "config.acknowledgement.already_used",
                "config.acknowledgement.unknown", "config.active.absent", "config.apply.kind_mismatch",
                "config.document.missing", "config.draft.apply_in_progress", "config.draft.cancelled",
                "config.draft.changed_during_apply", "config.draft.concurrent_edit", "config.path.not_listable",
                "config.path.unknown", "config.preview.required", "config.preview.stale", "config.revision.stale",
                "setup.preview.required", "stage.change.remap_invalid");
        Set<String> discriminated = Set.of("config.apply.failed", "config.validation.blocked");
        java.util.HashSet<String> reviewed = new java.util.HashSet<>(compatible);
        reviewed.addAll(discriminated);
        assertEquals(expected.keySet(), reviewed);

        Map<net.maddkraft.maddprestige.core.admin.AdministrationSemanticVariant, String> variants =
                net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation
                        .administrationSemanticVariantIdentities();
        assertEquals(Set.of(net.maddkraft.maddprestige.core.admin.AdministrationSemanticVariant.values()),
                variants.keySet());
        assertEquals(5, new java.util.HashSet<>(variants.values()).size());
        variants.forEach((variant, identity) -> {
            assertTrue(messages.requiredKeys().contains(
                    "command.error.administration." + identity + ".summary"), variant.name());
            assertTrue(messages.requiredKeys().contains(
                    "command.error.administration." + identity + ".remediation"), variant.name());
        });
    }

    @Test
    @DisplayName("[A68][OR8D-11] Every production single-source administration code has an audit entry")
    void administrationSingleSourceInventoryMatchesSemanticAudit() throws Exception {
        Path root = repositoryRoot();
        Pattern constructor = Pattern.compile(
                "new\\s+AdministrationException\\s*\\(\\s*\"([^\"]+)\"");
        Map<String, Integer> counts = new java.util.TreeMap<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java"))
                    .filter(value -> !value.toString().replace('\\', '/').contains("/target/"))
                    .filter(value -> value.toString().replace('\\', '/').contains("/src/main/java/"))
                    .toList()) {
                var matcher = constructor.matcher(Files.readString(path, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    counts.merge(matcher.group(1), 1, Integer::sum);
                }
            }
        }
        Set<String> singleSource = counts.entrySet().stream().filter(entry -> entry.getValue() == 1)
                .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        Set<String> multiSource = counts.entrySet().stream().filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));

        String audit = Files.readString(root.resolve(
                "docs/V2_PHASE8D_ADMINISTRATION_SINGLE_SOURCE_AUDIT.md"), StandardCharsets.UTF_8);
        var rows = Pattern.compile("(?m)^\\| `([^`]+)` \\| `[^`]+:\\d+` \\|").matcher(audit);
        Set<String> audited = new java.util.TreeSet<>();
        while (rows.find()) {
            assertTrue(audited.add(rows.group(1)), "duplicate single-source audit row: " + rows.group(1));
        }

        assertEquals(70, singleSource.size());
        assertEquals(19, multiSource.size());
        assertEquals(singleSource, audited,
                "a new or reclassified single-source code requires deliberate semantic-audit evidence");
        java.util.HashSet<String> accounted = new java.util.HashSet<>(audited);
        accounted.addAll(multiSource);
        assertEquals(net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation
                .knownAdministrationCodes(), accounted);
    }

    @Test
    @DisplayName("[A68][OR8D-09] Occurrence variants render exact recovery and validation semantics")
    void administrationOccurrenceVariantsRenderExactSemantics() {
        List<String> unchanged = administrationVariantLines("config.apply.failed",
                net.maddkraft.maddprestige.core.admin.AdministrationSemanticVariant
                        .CONFIG_APPLY_PRIOR_STATE_UNCHANGED,
                "revision", "r-safe");
        List<String> restored = administrationVariantLines("config.apply.failed",
                net.maddkraft.maddprestige.core.admin.AdministrationSemanticVariant
                        .CONFIG_APPLY_PRIOR_STATE_RESTORED,
                "revision", "r-restored");
        List<String> uncertain = administrationVariantLines("config.apply.failed",
                net.maddkraft.maddprestige.core.admin.AdministrationSemanticVariant
                        .CONFIG_APPLY_RECONCILIATION_REQUIRED,
                "revision", "r-uncertain");
        List<String> acknowledgement = administrationVariantLines("config.validation.blocked",
                net.maddkraft.maddprestige.core.admin.AdministrationSemanticVariant
                        .CONFIG_VALIDATION_ACKNOWLEDGEMENT_PREPARATION,
                "errors", 2, "findings", 1);
        List<String> apply = administrationVariantLines("config.validation.blocked",
                net.maddkraft.maddprestige.core.admin.AdministrationSemanticVariant.CONFIG_VALIDATION_APPLY,
                "errors", 0, "findings", 2);
        List<String> collection = administrationLines("config.path.not_listable", "path", "prestige.enabled");
        List<String> remap = administrationLines("stage.change.remap_snapshot_stale",
                "source", "first", "target", "second", "before", 1, "after", 2);

        assertTrue(unchanged.stream().anyMatch(line -> line.contains("r-safe") && line.contains("prior state")));
        assertTrue(restored.stream().anyMatch(line -> line.contains("r-restored")
                && line.contains("restored safely")));
        assertFalse(restored.stream().anyMatch(line -> line.contains("reconciliation")));
        assertTrue(uncertain.stream().anyMatch(line -> line.contains("could not be restored")
                && line.contains("uncertain")));
        assertTrue(uncertain.stream().anyMatch(line -> line.contains("do not retry")
                && line.contains("reconciliation")));
        assertTrue(acknowledgement.stream().anyMatch(line -> line.contains("acknowledgement preparation")));
        assertTrue(acknowledgement.stream().anyMatch(line -> line.contains("not the blocking cause")));
        assertFalse(acknowledgement.stream().anyMatch(
                line -> line.contains("unacknowledged high-risk findings block")));
        assertTrue(apply.stream().anyMatch(line -> line.contains("2 unacknowledged high-risk")));
        assertTrue(collection.stream().anyMatch(line -> line.contains("list/map collection")));
        assertTrue(collection.stream().anyMatch(line -> line.contains("config get/explain")));
        assertFalse(collection.stream().anyMatch(line -> line.contains("canonical list path")));
        assertTrue(remap.stream().anyMatch(line -> line.contains("Persisted player stage references")
                && line.contains("first") && line.contains("second")));
        assertFalse(remap.stream().anyMatch(line -> line.contains("candidate configuration")));
        assertFalse(java.util.stream.Stream.of(unchanged, restored, uncertain, acknowledgement, apply,
                collection, remap).flatMap(List::stream).anyMatch(line -> line.contains("INTERNAL")));
    }

    @Test
    @DisplayName("[A68][OR8D-11] Corrected catalog prose identifies exact source objects and consequences")
    void correctedAdministrationCatalogMatchesSingleSourceConditions() {
        List<String> previewStale = administrationLines("config.preview.stale");
        List<String> revisionStale = administrationLines("config.revision.stale");
        List<String> rollbackDraft = administrationLines("config.rollback.not_prepared");
        List<String> rollbackHistory = administrationLines("config.rollback.not_applied",
                "revision", "r-old", "status", "FAILED");
        List<String> snapshotPreparation = administrationLines("config.snapshot.prepare_failed");
        List<String> actorMismatch = administrationLines("confirmation.actor_mismatch");
        List<String> unknownConfirmation = administrationLines("confirmation.unknown");
        List<String> stageAdd = administrationLines("stage.add.rejected", "stage", "member");
        List<String> remapAbsent = administrationLines("stage.change.remap_missing");
        List<String> replacementAbsent = administrationLines("stage.change.remap_required");
        List<String> stageRemove = administrationLines("stage.remove.rejected",
                "stage", "legacy", "target", "member");

        assertTrue(previewStale.stream().anyMatch(line -> line.contains("draft changed after this preview")));
        assertFalse(previewStale.stream().anyMatch(line -> line.toLowerCase(java.util.Locale.ROOT)
                .contains("active revision")));
        assertTrue(revisionStale.stream().anyMatch(line -> line.contains("active configuration changed")));
        assertFalse(previewStale.equals(revisionStale));

        assertTrue(rollbackDraft.stream().anyMatch(line -> line.contains("configuration draft")
                && line.contains("rollback workflow")));
        assertTrue(rollbackDraft.stream().anyMatch(line -> line.contains("APPLIED revision")
                && line.contains("preview")));
        assertFalse(rollbackDraft.stream().anyMatch(line -> line.contains("historical revision has not")));
        assertTrue(rollbackHistory.stream().anyMatch(line -> line.contains("Historical revision r-old")
                && line.contains("FAILED")));
        assertFalse(rollbackDraft.equals(rollbackHistory));

        assertTrue(snapshotPreparation.stream().anyMatch(line -> line.contains("failed before activation")));
        assertTrue(snapshotPreparation.stream().anyMatch(line -> line.contains("remains unchanged")
                && line.contains("authoritative")));
        assertFalse(snapshotPreparation.stream().anyMatch(line -> line.contains("partially activated")
                || line.contains("reconciliation is required")));

        assertTrue(actorMismatch.stream().anyMatch(line -> line.contains("different actor")));
        assertFalse(actorMismatch.stream().anyMatch(line -> line.contains("different player")));
        assertTrue(unknownConfirmation.stream().anyMatch(line -> line.contains("expired")
                && line.contains("already used")));
        assertTrue(stageAdd.stream().anyMatch(line -> line.contains("member")
                && line.contains("losslessly") && line.contains("progression.yml")));
        assertTrue(stageAdd.stream().anyMatch(line -> line.contains("unique immutable stage ID")
                && line.contains("block-style")));
        assertTrue(remapAbsent.stream().anyMatch(line -> line.contains("no stage-remap plan")
                && line.contains("mapping")));
        assertTrue(replacementAbsent.stream().anyMatch(line -> line.contains("GUI stage-deletion action")
                && line.contains("no explicit replacement")));
        assertFalse(replacementAbsent.stream().anyMatch(line -> line.contains("referenced stage")));
        assertTrue(stageRemove.stream().anyMatch(line -> line.contains("legacy")
                && line.contains("losslessly") && line.contains("progression.yml")));
        assertFalse(java.util.stream.Stream.of(previewStale, revisionStale, rollbackDraft, rollbackHistory,
                        snapshotPreparation, actorMismatch, unknownConfirmation, stageAdd, remapAbsent,
                        replacementAbsent, stageRemove).flatMap(List::stream)
                .anyMatch(line -> line.contains("INTERNAL")));
    }

    @Test
    @DisplayName("[A68][OR8D-11] Alternate catalogs own corrected semantics without changing facts")
    void alternateCatalogControlsCorrectedAdministrationSemantics() throws Exception {
        select("zz_ZZ", """
                command.error.administration.config_preview_stale.summary: "ALT DRAFT <code>"
                command.error.administration.config_preview_stale.remediation: "ALT REPREVIEW"
                command.error.administration.config_rollback_not_prepared.summary: "ALT ROLLBACK DRAFT <code>"
                command.error.administration.config_rollback_not_prepared.remediation: "ALT PREPARE"
                command.error.administration.config_snapshot_prepare_failed.summary: "ALT PRE-ACTIVATION <code>"
                command.error.administration.config_snapshot_prepare_failed.remediation: "ALT UNCHANGED"
                command.error.administration.confirmation_actor_mismatch.summary: "ALT ACTOR <code>"
                command.error.administration.confirmation_actor_mismatch.remediation: "ALT OWNER"
                command.error.administration.stage_add_rejected.summary: "ALT STAGE <stage> <code>"
                command.error.administration.stage_add_rejected.remediation: "ALT LOSSLESS"
                """);
        assertTrue(messages.reload().successful());

        assertEquals(List.of("ALT DRAFT config.preview.stale", "ALT REPREVIEW"),
                administrationLines("config.preview.stale"));
        assertEquals(List.of("ALT ROLLBACK DRAFT config.rollback.not_prepared", "ALT PREPARE"),
                administrationLines("config.rollback.not_prepared"));
        assertEquals(List.of("ALT PRE-ACTIVATION config.snapshot.prepare_failed", "ALT UNCHANGED"),
                administrationLines("config.snapshot.prepare_failed"));
        assertEquals(List.of("ALT ACTOR confirmation.actor_mismatch", "ALT OWNER"),
                administrationLines("confirmation.actor_mismatch"));

        var exception = new net.maddkraft.maddprestige.core.admin.AdministrationException(
                "stage.add.rejected", "INTERNAL SUMMARY", "INTERNAL REMEDIATION", "stage", "member");
        List<net.maddkraft.maddprestige.core.admin.presentation.MessageReference> references =
                net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation.administration(exception);
        assertEquals(Map.of("code", "stage.add.rejected", "stage", "member"),
                references.getFirst().arguments());
        assertEquals(references.getFirst().arguments(), references.getLast().arguments());
        assertEquals(List.of("ALT STAGE member stage.add.rejected", "ALT LOSSLESS"), render(references));
        assertFalse(render(references).stream().anyMatch(line -> line.contains("INTERNAL")));
    }

    @Test
    @DisplayName("[A68][OR8D-09] Alternate catalogs own every newly distinguished occurrence state")
    void alternateCatalogControlsAdministrationOccurrenceVariants() throws Exception {
        select("zz_ZZ", """
                command.error.administration.config_apply_failed_prior_state_unchanged.summary: "ALT UNCHANGED <revision> <variant>"
                command.error.administration.config_apply_failed_prior_state_unchanged.remediation: "ALT FRESH PREVIEW"
                command.error.administration.config_apply_failed_prior_state_restored.summary: "ALT RESTORED <revision> <variant>"
                command.error.administration.config_apply_failed_prior_state_restored.remediation: "ALT SAFE RETRY"
                command.error.administration.config_apply_failed_reconciliation_required.summary: "ALT UNCERTAIN <revision> <variant>"
                command.error.administration.config_apply_failed_reconciliation_required.remediation: "ALT RECONCILE"
                command.error.administration.config_validation_blocked_acknowledgement_preparation.summary: "ALT ACK <errors> <findings> <variant>"
                command.error.administration.config_validation_blocked_acknowledgement_preparation.remediation: "ALT FIX ERRORS"
                command.error.administration.config_validation_blocked_apply.summary: "ALT APPLY <errors> <findings> <variant>"
                command.error.administration.config_validation_blocked_apply.remediation: "ALT ACK RISKS"
                command.error.administration.stage_change_remap_snapshot_stale.summary: "ALT REMAP <source> <target> <before> <after>"
                command.error.administration.stage_change_remap_snapshot_stale.remediation: "ALT REPREVIEW"
                """);
        assertTrue(messages.reload().successful());

        assertEquals(List.of("ALT UNCHANGED r-zero CONFIG_APPLY_PRIOR_STATE_UNCHANGED", "ALT FRESH PREVIEW"),
                administrationVariantLines("config.apply.failed",
                        net.maddkraft.maddprestige.core.admin.AdministrationSemanticVariant
                                .CONFIG_APPLY_PRIOR_STATE_UNCHANGED,
                        "revision", "r-zero"));
        assertEquals(List.of("ALT RESTORED r-one CONFIG_APPLY_PRIOR_STATE_RESTORED", "ALT SAFE RETRY"),
                administrationVariantLines("config.apply.failed",
                        net.maddkraft.maddprestige.core.admin.AdministrationSemanticVariant
                                .CONFIG_APPLY_PRIOR_STATE_RESTORED,
                        "revision", "r-one"));
        assertEquals(List.of("ALT UNCERTAIN r-two CONFIG_APPLY_RECONCILIATION_REQUIRED", "ALT RECONCILE"),
                administrationVariantLines("config.apply.failed",
                        net.maddkraft.maddprestige.core.admin.AdministrationSemanticVariant
                                .CONFIG_APPLY_RECONCILIATION_REQUIRED,
                        "revision", "r-two"));
        assertEquals(List.of("ALT ACK 2 3 CONFIG_VALIDATION_ACKNOWLEDGEMENT_PREPARATION", "ALT FIX ERRORS"),
                administrationVariantLines("config.validation.blocked",
                        net.maddkraft.maddprestige.core.admin.AdministrationSemanticVariant
                                .CONFIG_VALIDATION_ACKNOWLEDGEMENT_PREPARATION,
                        "errors", 2, "findings", 3));
        assertEquals(List.of("ALT APPLY 0 2 CONFIG_VALIDATION_APPLY", "ALT ACK RISKS"),
                administrationVariantLines("config.validation.blocked",
                        net.maddkraft.maddprestige.core.admin.AdministrationSemanticVariant.CONFIG_VALIDATION_APPLY,
                        "errors", 0, "findings", 2));
        assertEquals(List.of("ALT REMAP first second 1 2", "ALT REPREVIEW"),
                administrationLines("stage.change.remap_snapshot_stale", "source", "first", "target", "second",
                        "before", 1, "after", 2));
    }

    @Test
    @DisplayName("[A68][OR8D-09] Reviewed administration cases render exact facts and actionable meaning")
    void reviewedAdministrationCasesRenderExactSemantics() {
        List<String> authority = administrationLines("config.acknowledgement.server_authority_required");
        List<String> notRequired = administrationLines("config.acknowledgement.not_required");
        List<String> group = administrationLines("setup.group.missing",
                "stage", "veteran", "provider", "luckperms");
        List<String> duplicate = administrationLines("setup.requirement.duplicate",
                "requirement", "playtime", "stage", "veteran");
        List<String> prestige = administrationLines("setup.prestige.stage_unknown",
                "purpose", "reset", "stage", "legacy");
        List<String> metric = administrationLines("setup.requirement.metric_unknown",
                "provider", "statistics", "metric", "missing_metric");
        List<String> document = administrationLines("config.document.missing",
                "document", "progression.yml");
        List<String> source = administrationLines("stage.change.remap_source_present",
                "source", "legacy", "target", "member");
        List<String> target = administrationLines("stage.change.remap_target_missing",
                "source", "legacy", "target", "disabled");
        List<String> missingWorkflow = administrationLines("config.draft.unknown");
        List<String> actorMismatch = administrationLines("config.acknowledgement.actor_mismatch");

        assertTrue(authority.stream().anyMatch(line -> line.contains("Caller-supplied")));
        assertTrue(authority.stream().anyMatch(line -> line.contains("server-issued")));
        assertFalse(authority.stream().anyMatch(line -> line.contains("different actor")));
        assertFalse(authority.equals(actorMismatch));
        assertTrue(actorMismatch.stream().anyMatch(line -> line.contains("different administrator")));
        assertTrue(notRequired.stream().anyMatch(line -> line.contains("no high-risk findings")));
        assertTrue(notRequired.stream().anyMatch(line -> line.contains("normal configuration apply")));
        assertFalse(notRequired.stream().anyMatch(line -> line.contains("missing")
                || line.contains("absent") || line.contains("reopen the relevant workflow")));
        assertTrue(group.stream().anyMatch(line -> line.contains("veteran") && line.contains("luckperms")));
        assertTrue(group.stream().anyMatch(line -> line.contains("never creates external groups")));
        assertFalse(group.equals(missingWorkflow));
        assertFalse(group.stream().anyMatch(line -> line.contains("draft") || line.contains("session")
                || line.contains("revision") || line.contains("server-owned action")));
        assertTrue(missingWorkflow.stream().anyMatch(line -> line.contains("draft")));
        assertTrue(duplicate.stream().anyMatch(line -> line.contains("playtime")));
        assertTrue(duplicate.stream().anyMatch(line -> line.contains("veteran")));
        assertTrue(duplicate.stream().anyMatch(line -> line.contains("unique immutable requirement ID")));
        assertTrue(prestige.stream().anyMatch(line -> line.contains("reset") && line.contains("legacy")));
        assertTrue(metric.stream().anyMatch(line -> line.contains("statistics")
                && line.contains("missing_metric") && line.contains("authoritative value type")));
        assertTrue(document.stream().anyMatch(line -> line.contains("progression.yml")
                && line.contains("missing")));
        assertTrue(source.stream().anyMatch(line -> line.contains("legacy") && line.contains("still exists")));
        assertTrue(target.stream().anyMatch(line -> line.contains("disabled")
                && line.contains("absent, disabled, or unordered")));
        assertFalse(java.util.stream.Stream.of(authority, notRequired, group, duplicate, prestige, metric,
                        document, source, target).flatMap(List::stream)
                .anyMatch(line -> line.contains("INTERNAL")));
    }

    @Test
    @DisplayName("[A76][OR8F-A76-01] Requirement input diagnostics render exact owner-facing semantics")
    void requirementInputDiagnosticsRenderExactOwnerFacingSemantics() {
        List<String> target = administrationLines("setup.requirement.target.invalid",
                "target", "one-minute", "type", "DURATION", "provider", "paper_statistics",
                "metric", "play_one_minute");
        List<String> operator = administrationLines("setup.requirement.operator.invalid",
                "operator", "IN_RANGE", "type", "DURATION", "provider", "paper_statistics",
                "metric", "play_one_minute", "allowed", "GREATER_OR_EQUAL, EQUAL");
        List<String> scope = administrationLines("setup.requirement.scope.invalid",
                "scope", "EVER", "provider", "paper_statistics", "metric", "play_one_minute",
                "allowed", "ABSOLUTE, LIFETIME, SINCE_STAGE_START, SINCE_PRESTIGE_START, SINCE_SEASON_START");
        List<String> completion = administrationLines("setup.requirement.completion.invalid",
                "completion", "ONCE", "provider", "paper_statistics", "metric", "play_one_minute",
                "allowed", "LIVE, LATCHED");
        List<String> ancestry = administrationLines("setup.draft.invalid");

        assertTrue(target.stream().anyMatch(line -> line.contains("one-minute") && line.contains("DURATION")));
        assertTrue(target.stream().anyMatch(line -> line.contains("PT1M") && line.contains("1m")));
        assertFalse(target.stream().anyMatch(line -> line.contains("ancestry") || line.contains("rollback")
                || line.contains("first-run") || line.contains("active configuration")));
        assertTrue(operator.stream().anyMatch(line -> line.contains("IN_RANGE") && line.contains("DURATION")));
        assertTrue(operator.stream().anyMatch(line -> line.contains("GREATER_OR_EQUAL")));
        assertTrue(scope.stream().anyMatch(line -> line.contains("EVER")));
        assertTrue(scope.stream().anyMatch(line -> line.contains("SINCE_PRESTIGE_START")));
        assertTrue(completion.stream().anyMatch(line -> line.contains("ONCE")));
        assertTrue(completion.stream().anyMatch(line -> line.contains("LIVE") && line.contains("LATCHED")));
        assertTrue(ancestry.stream().anyMatch(line -> line.contains("active or rollback ancestry")));
        assertTrue(ancestry.stream().anyMatch(line -> line.contains("normal configuration or rollback workflow")));
    }

    @Test
    @DisplayName("[A68][OR8D-09] Alternate catalogs own acknowledgement, group, and duplicate semantics")
    void alternateCatalogControlsReviewedAdministrationFamiliesAndFacts() throws Exception {
        select("zz_ZZ", """
                command.error.administration.config_acknowledgement_server_authority_required.summary: "ALT AUTH <code>"
                command.error.administration.config_acknowledgement_server_authority_required.remediation: "ALT SERVER TOKEN"
                command.error.administration.setup_group_missing.summary: "ALT GROUP <stage> <provider>"
                command.error.administration.setup_group_missing.remediation: "ALT EXISTING GROUP"
                command.error.administration.setup_requirement_duplicate.summary: "ALT DUP <requirement> <stage>"
                command.error.administration.setup_requirement_duplicate.remediation: "ALT UNIQUE REQUIREMENT"
                """);
        assertTrue(messages.reload().successful());

        assertEquals(List.of("ALT AUTH config.acknowledgement.server_authority_required", "ALT SERVER TOKEN"),
                administrationLines("config.acknowledgement.server_authority_required"));
        assertEquals(List.of("ALT GROUP veteran luckperms", "ALT EXISTING GROUP"),
                administrationLines("setup.group.missing", "stage", "veteran", "provider", "luckperms"));
        assertEquals(List.of("ALT DUP playtime veteran", "ALT UNIQUE REQUIREMENT"),
                administrationLines("setup.requirement.duplicate",
                        "requirement", "playtime", "stage", "veteran"));
    }

    @Test
    @DisplayName("[A68][OR8D-09] Administration facts survive key selection and unknown codes fail safely")
    void administrationFactsSurviveSelectionAndUnknownCodesUseSafeFallback() {
        var exact = new net.maddkraft.maddprestige.core.admin.AdministrationException(
                "setup.requirement.duplicate", "INTERNAL", "INTERNAL",
                "requirement", "playtime", "stage", "veteran");
        List<net.maddkraft.maddprestige.core.admin.presentation.MessageReference> references =
                net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation.administration(exact);
        assertEquals(Map.of("code", "setup.requirement.duplicate", "requirement", "playtime",
                "stage", "veteran"), references.getFirst().arguments());
        assertEquals(references.getFirst().arguments(), references.getLast().arguments());

        var unknown = new net.maddkraft.maddprestige.core.admin.AdministrationException(
                "future.failure", "INTERNAL", "INTERNAL", "stage", "future");
        List<net.maddkraft.maddprestige.core.admin.presentation.MessageReference> fallback =
                net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation.administration(unknown);
        assertEquals("command.error.administration.general.summary", fallback.getFirst().key());
        assertEquals("future.failure", fallback.getFirst().arguments().get("code"));
        assertTrue(render(fallback).stream().anyMatch(line -> line.contains("future.failure")));
        assertFalse(render(fallback).stream().anyMatch(line -> line.contains("INTERNAL")));
    }

    @Test
    @DisplayName("[A68][OR8D-07] Eligible and blocked previews render structured operation-plan facts")
    void semanticPreviewRendersStructuredPlanAndBlockers() {
        var revision = new net.maddkraft.maddprestige.api.id.ConfigRevisionId("r-plan");
        var details = List.of(
                net.maddkraft.maddprestige.core.admin.presentation.MessageReference.of(
                        "command.preview.state_change", "current_stage", "member", "target_stage", "adventurer"),
                net.maddkraft.maddprestige.core.admin.presentation.MessageReference.of(
                        "command.preview.cost", "id", "coins", "provider", "vault", "type", "debit",
                        "amount", "25", "value", "Twenty-five coins"),
                net.maddkraft.maddprestige.core.admin.presentation.MessageReference.of(
                        "command.preview.reward", "id", "badge", "provider", "internal", "type", "grant",
                        "amount", "1", "value", "Adventurer badge"));
        var eligible = new net.maddkraft.maddprestige.core.admin.OperationPreview(
                net.maddkraft.maddprestige.core.admin.OperationKind.RANK_UP, java.util.UUID.randomUUID(), true,
                "PRECOMPOSED_STATE_SENTINEL", java.util.Optional.empty(), List.of("COST_SENTINEL"),
                List.of("REWARD_SENTINEL"), List.of(), List.of(), revision, Map.of(), List.of(), details);
        var blocked = new net.maddkraft.maddprestige.core.admin.OperationPreview(
                net.maddkraft.maddprestige.core.admin.OperationKind.RANK_UP, java.util.UUID.randomUUID(), false,
                "PRECOMPOSED_STATE_SENTINEL", java.util.Optional.empty(), List.of(), List.of(), List.of(),
                List.of("Provider is unavailable or stale: vault"), revision, Map.of(), List.of(),
                List.of(details.getFirst()), List.of(blocker(
                        net.maddkraft.maddprestige.core.authorization.AuthorizationBlockerKind
                                .REQUIRED_PROVIDER_UNAVAILABLE,
                        "Provider is unavailable or stale: vault", "provider", "vault")));

        List<String> eligibleOutput = render(net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation
                .preview("command.simulation.summary", eligible));
        List<String> blockedOutput = render(net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation
                .preview("command.simulation.summary", blocked));

        assertTrue(eligibleOutput.stream().anyMatch(value -> value.contains("member")
                && value.contains("adventurer")));
        assertTrue(eligibleOutput.stream().anyMatch(value -> value.contains("coins")
                && value.contains("vault") && value.contains("25")));
        assertTrue(eligibleOutput.stream().anyMatch(value -> value.contains("badge")
                && value.contains("Adventurer badge")));
        assertTrue(eligibleOutput.stream().anyMatch(value -> value.contains("r-plan")));
        assertTrue(blockedOutput.stream().anyMatch(value -> value.contains("vault")
                && value.contains("absent")));
        assertFalse(java.util.stream.Stream.concat(eligibleOutput.stream(), blockedOutput.stream())
                .anyMatch(value -> value.contains("SENTINEL")));
    }

    @Test
    @DisplayName("[A68][OR8D-07] Alternate catalogs translate restored Why, Doctor, and preview semantics")
    void alternateCatalogControlsRestoredSemanticConcepts() throws Exception {
        select("zz_ZZ", """
                command.why.blocker.required_provider_unavailable: "ALT WHY <provider>"
                command.doctor.finding.database.summary: "ALT DOCTOR <path> <code>"
                command.doctor.finding.database.remediation: "ALT REMEDY <path>"
                command.preview.cost: "ALT COST <id> <provider> <amount>"
                """);
        assertTrue(messages.reload().successful());

        var blocker = net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation
                .authorizationBlocker(blocker(
                        net.maddkraft.maddprestige.core.authorization.AuthorizationBlockerKind
                                .REQUIRED_PROVIDER_UNAVAILABLE,
                        "Provider is unavailable or stale: vault", "provider", "vault"));
        var doctor = new net.maddkraft.maddprestige.core.admin.diagnostic.DiagnosticFinding(
                "database.unreachable",
                net.maddkraft.maddprestige.core.admin.diagnostic.DiagnosticSeverity.BLOCKED,
                "database", "database.connection", "ENGLISH_SUMMARY", "ENGLISH_REMEDIATION");
        var cost = net.maddkraft.maddprestige.core.admin.presentation.MessageReference.of(
                "command.preview.cost", "id", "coins", "provider", "vault", "type", "debit",
                "amount", "25", "value", "Coins");

        assertEquals("ALT WHY vault", plain(messages.render(blocker)));
        assertEquals(List.of("ALT DOCTOR database.connection database.unreachable",
                "ALT REMEDY database.connection"), render(
                        net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation
                                .doctorFinding(doctor)));
        assertEquals("ALT COST coins vault 25", plain(messages.render(cost)));
    }

    @Test
    @DisplayName("[A68][OR8D-08][OR8D-10] Alternate catalogs own precise blockers and no-plan failures")
    void alternateCatalogControlsPreciseBlockerAdministrationAndNoPlanSemantics() throws Exception {
        select("zz_ZZ", """
                command.why.blocker.prestige_maximum_reached: "ALT MAX <current_prestige>/<prestige_maximum>"
                command.error.administration.config_path_unknown.summary: "ALT PATH <path>"
                command.error.administration.config_path_unknown.remediation: "ALT PATH REMEDY"
                command.error.administration.operation_preview_blocked.summary: "ALT PREVIEW <operation>"
                command.error.administration.operation_preview_blocked.remediation: "ALT PREVIEW REMEDY"
                """);
        assertTrue(messages.reload().successful());

        var maximum = blocker(
                net.maddkraft.maddprestige.core.authorization.AuthorizationBlockerKind.PRESTIGE_MAXIMUM_REACHED,
                "INTERNAL", "current_prestige", 5, "prestige_maximum", 5);
        assertEquals("ALT MAX 5/5", plain(messages.render(
                net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation
                        .authorizationBlocker(maximum))));
        assertEquals(List.of("ALT PATH prestige.unknown", "ALT PATH REMEDY"), render(
                net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation.administration(
                        new net.maddkraft.maddprestige.core.admin.AdministrationException(
                                "config.path.unknown", "INTERNAL", "INTERNAL",
                                "path", "prestige.unknown"))));
        assertEquals(List.of("ALT PREVIEW Prestige", "ALT MAX 5/5", "ALT PREVIEW REMEDY"), render(
                net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation.administration(
                        net.maddkraft.maddprestige.core.admin.AdministrationException
                                .authorizationRejected("Prestige", List.of(maximum)))));
    }

    @Test
    void setupIntegrationDiagnosticRendersEveryStructuredFactUnderBundledAndAlternateCatalogs() throws Exception {
        Object[] facts = {"provider", "placeholder_input", "component", "placeholderapi",
                "requirement", "placeholder, value type, and maximum age"};
        List<String> bundled = administrationLines("setup.integration.unconfigurable", facts);
        assertTrue(bundled.getFirst().contains("placeholder_input"));
        assertTrue(bundled.getFirst().contains("placeholderapi"));
        assertTrue(bundled.getFirst().contains("placeholder, value type, and maximum age"));
        assertFalse(bundled.stream().anyMatch(line -> line.contains("<provider>") || line.contains("<component>")
                || line.contains("<requirement>") || line.contains("INTERNAL")));

        select("zz_ZZ", """
                command.error.administration.setup_integration_unconfigurable.summary: "ALT <provider>|<component>|<requirement>"
                command.error.administration.setup_integration_unconfigurable.remediation: "ALT REMEDY"
                """);
        assertTrue(messages.reload().successful());
        assertEquals(List.of("ALT placeholder_input|placeholderapi|placeholder, value type, and maximum age",
                "ALT REMEDY"), administrationLines("setup.integration.unconfigurable", facts));
    }

    @Test
    @DisplayName("[A68][OR8D-07] Every schema explanation identity has bundled catalog coverage")
    void configurationDescriptionCatalogIsComplete() {
        var schema = net.maddkraft.maddprestige.core.schema.PhaseSixSchema.create();
        schema.nodes().forEach(node -> assertTrue(messages.requiredKeys().contains(String.join(".",
                "command", "config", "description", node.id().value())), node.id().value()));
    }

    @Test
    @DisplayName("[A68] Paper presentation sites do not bypass the catalog with literal Component text")
    void presentationSitesUseCatalog() throws Exception {
        Path invocationDirectory = Path.of("").toAbsolutePath();
        Path source = invocationDirectory.resolve("src/main/java");
        if (!Files.isDirectory(source)) {
            source = invocationDirectory.resolve("maddprestige-platform-paper/src/main/java");
        }
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(source)) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java")).toList()) {
                String text = Files.readString(path, StandardCharsets.UTF_8);
                if (text.contains("sendMessage(Component.text(")
                        || text.contains("displayName(Component.text(")
                        || createsInventoryWithLiteralText(text)) {
                    violations.add(source.relativize(path).toString());
                }
            }
        }
        assertEquals(List.of(), violations);
    }

    private static boolean createsInventoryWithLiteralText(String source) {
        String inventoryCall = "Bukkit.createInventory(";
        String literalText = "Component.text(";
        int searchFrom = 0;
        while (searchFrom < source.length()) {
            int inventory = source.indexOf(inventoryCall, searchFrom);
            if (inventory < 0) {
                return false;
            }
            int statementEnd = source.indexOf(';', inventory + inventoryCall.length());
            int searchEnd = statementEnd < 0 ? source.length() : statementEnd;
            int literal = source.indexOf(literalText, inventory + inventoryCall.length(), searchEnd);
            if (literal >= 0) {
                return true;
            }
            searchFrom = statementEnd < 0 ? source.length() : statementEnd + 1;
        }
        return false;
    }

    @Test
    @DisplayName("[A68][OR8D-01] Every public command, GUI, and Phase 7 key has bundled catalog coverage")
    void publicPresentationKeyInventoryIsCompleteAndHasNoGenericWrappers() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("maddprestige-core/src/main/java"))) {
            root = root.getParent();
        }
        assertTrue(root != null, "repository root");
        Pattern reference = Pattern.compile("\\\"((?:command|gui|phase7)\\.[a-z0-9_.-]+)\\\"");
        java.util.TreeSet<String> referenced = new java.util.TreeSet<>();
        for (Path source : List.of(root.resolve("maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/admin"),
                root.resolve("maddprestige-platform-paper/src/main/java/net/maddkraft/maddprestige/platform/paper"))) {
            try (var paths = Files.walk(source)) {
                for (Path path : paths.filter(value -> value.toString().endsWith(".java")).toList()) {
                    var matcher = reference.matcher(Files.readString(path, StandardCharsets.UTF_8));
                    while (matcher.find()) {
                        if (!matcher.group(1).endsWith(".")) {
                            referenced.add(matcher.group(1));
                        }
                    }
                }
            }
        }
        assertTrue(messages.requiredKeys().containsAll(referenced), () -> {
            var missing = new java.util.TreeSet<>(referenced);
            missing.removeAll(messages.requiredKeys());
            return "Missing public catalog keys: " + missing;
        });
        assertTrue(java.util.Collections.disjoint(messages.requiredKeys(), Set.of(
                "command.line", "gui.inventory.title", "gui.inventory.action", "gui.action.result",
                "phase7.line")));
    }

    @Test
    @DisplayName("[A68][OR8D-08][OR8D-09] Semantic classifiers cannot parse blocker prose or code fragments")
    void semanticPresentationDoesNotUseLossyPublicClassifiers() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve(
                "maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/admin/presentation/"
                        + "SemanticPresentation.java"))) {
            root = root.getParent();
        }
        assertTrue(root != null, "repository root");
        String source = Files.readString(root.resolve(
                "maddprestige-core/src/main/java/net/maddkraft/maddprestige/core/admin/presentation/"
                        + "SemanticPresentation.java"), StandardCharsets.UTF_8);
        assertFalse(source.contains("whyBlocker(String"));
        assertFalse(source.contains("administrationCategory("));
        assertFalse(source.contains("toLowerCase(Locale.ROOT)"));
        for (String fragment : List.of("code.contains(\"unknown\")", "code.contains(\"missing\")",
                "code.contains(\"stale\")", "code.contains(\"invalid\")", "code.contains(\"failed\")")) {
            assertFalse(source.contains(fragment), fragment);
        }
    }

    @Test
    @DisplayName("[A68][OR8D-03] Valid locale paths remain contained and readable")
    void acceptsContainedRegularLocaleFiles() throws Exception {
        select("zz_ZZ", "command.failed: \"Contained\"\n");

        assertTrue(messages.reload().successful());
        assertEquals("Contained", plain(messages.render("command.failed")));
    }

    @Test
    @DisplayName("[A68][OR8D-03] Final locale and selection-file symlinks reject before escaped content is read")
    void rejectsSymbolicLocaleFiles() throws Exception {
        Path escapedCatalog = temporaryDirectory.resolve("escaped-catalog.yml");
        Files.writeString(escapedCatalog, "command.failed: \"ESCAPED\"\n", StandardCharsets.UTF_8);
        Path selected = temporaryDirectory.resolve("locales/zz_ZZ.yml");
        createSymbolicPath(selected, escapedCatalog.toAbsolutePath());
        Files.writeString(temporaryDirectory.resolve("locale.yml"), "locale: zz_ZZ\n", StandardCharsets.UTF_8);

        assertFalse(messages.reload().successful());
        assertFalse(plain(messages.render("command.failed")).contains("ESCAPED"));

        Files.delete(selected);
        Path settings = temporaryDirectory.resolve("locale.yml");
        Files.delete(settings);
        Path escapedSelection = temporaryDirectory.resolve("escaped-selection.yml");
        Files.writeString(escapedSelection, "locale: en_US\n", StandardCharsets.UTF_8);
        createSymbolicPath(settings, escapedSelection.toAbsolutePath());

        assertFalse(messages.reload().successful());
    }

    @Test
    @DisplayName("[A68][OR8D-03] Locale-directory and intermediate-ancestor symlinks cannot escape containment")
    void rejectsSymbolicAncestorDirectories() throws Exception {
        Path contained = temporaryDirectory.resolve("plugin-data");
        PaperMessageService containedMessages = PaperMessageService.open(contained,
                PaperMessageService.class.getClassLoader(), diagnostics::add);
        Path localeDirectory = contained.resolve("locales");
        Files.delete(localeDirectory);
        Path escaped = temporaryDirectory.resolve("escaped-locales");
        Files.createDirectory(escaped);
        Files.writeString(escaped.resolve("zz_ZZ.yml"), "command.failed: \"ESCAPED\"\n",
                StandardCharsets.UTF_8);
        createSymbolicPath(localeDirectory, escaped.toAbsolutePath());
        Files.writeString(contained.resolve("locale.yml"), "locale: zz_ZZ\n", StandardCharsets.UTF_8);

        assertFalse(containedMessages.reload().successful());
        assertFalse(plain(containedMessages.render("command.failed")).contains("ESCAPED"));

        Path physicalParent = temporaryDirectory.resolve("physical-parent");
        Files.createDirectories(physicalParent.resolve("plugin-data"));
        Path linkedParent = temporaryDirectory.resolve("linked-parent");
        createSymbolicPath(linkedParent, physicalParent.toAbsolutePath());
        Path aliasedData = linkedParent.resolve("plugin-data");
        assertThrows(java.io.IOException.class, () -> PaperMessageService.open(aliasedData,
                PaperMessageService.class.getClassLoader(), diagnostics::add));
    }

    @Test
    @DisplayName("[A68][OR8D-03] Traversal and absolute locale selections reject without changing fallback")
    void rejectsTraversalAndAbsoluteLocaleSelections() throws Exception {
        Files.writeString(temporaryDirectory.resolve("locale.yml"), "locale: ../escaped\n",
                StandardCharsets.UTF_8);
        assertFalse(messages.reload().successful());
        assertFalse(plain(messages.render("command.failed")).contains("ESCAPED"));

        Files.writeString(temporaryDirectory.resolve("locale.yml"),
                "locale: \"" + temporaryDirectory.toAbsolutePath().toString().replace("\\", "\\\\") + "\"\n",
                StandardCharsets.UTF_8);
        assertFalse(messages.reload().successful());
        assertFalse(plain(messages.render("command.failed")).startsWith("[message:"));
    }

    private void select(String locale, String catalog) throws Exception {
        Files.writeString(temporaryDirectory.resolve("locale.yml"), "locale: " + locale + "\n",
                StandardCharsets.UTF_8);
        writeCatalog(locale, catalog);
    }

    private void writeCatalog(String locale, String catalog) throws Exception {
        Path directory = temporaryDirectory.resolve("locales");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(locale + ".yml"), catalog, StandardCharsets.UTF_8);
    }

    private static void createSymbolicPath(Path link, Path target) throws Exception {
        try {
            Files.createSymbolicLink(link, target);
        } catch (java.nio.file.FileSystemException exception) {
            if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("windows")
                    || !exception.getMessage().contains("privilege")) {
                throw exception;
            }
            Path junctionTarget = Files.isDirectory(target) ? target : target.getParent();
            Process process = new ProcessBuilder("cmd.exe", "/d", "/c", "mklink", "/J",
                    link.toString(), junctionTarget.toString()).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.waitFor() != 0 || Files.notExists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                throw new AssertionError("Could not create the Windows reparse-point adversary: " + output,
                        exception);
            }
        }
    }

    private static Map<String, String> arguments() {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        List.of("acknowledgement", "active", "after", "allowed", "amount", "base", "before", "blockers", "code",
                "completion", "component", "confirmation", "configured_cost", "configured_reward", "count",
                "counter",
                "cooldown_remaining", "currency", "current", "current_lifetime",
                "current_prestige", "current_stage", "delta", "detail", "disposition", "draft", "expires",
                "eligible_at", "findings", "group", "hash", "healthy", "id", "intended_target", "kind",
                "lifetime", "locale", "metric",
                "metrics", "operation", "path", "permission", "player", "prestige_status", "provider", "rank",
                "rankup_status", "remediation", "requirement", "prestige_maximum", "revision", "risk",
                "scope", "stage", "status",
                "target", "target_lifetime", "target_prestige", "target_stage", "topic", "type", "usage",
                "value", "version").forEach(name -> values.put(name, "value"));
        return Map.copyOf(values);
    }

    private static net.maddkraft.maddprestige.core.authorization.AuthorizationBlocker blocker(
            net.maddkraft.maddprestige.core.authorization.AuthorizationBlockerKind kind,
            String diagnostic,
            Object... facts) {
        return net.maddkraft.maddprestige.core.authorization.AuthorizationBlocker.of(kind, diagnostic, facts);
    }

    private static Map<String, String> blockerFacts() {
        LinkedHashMap<String, String> facts = new LinkedHashMap<>(arguments());
        facts.remove("acknowledgement");
        facts.remove("completion");
        facts.remove("confirmation");
        facts.remove("current_lifetime");
        facts.remove("prestige_status");
        facts.remove("rankup_status");
        facts.remove("target_lifetime");
        facts.remove("target_prestige");
        facts.remove("topic");
        facts.remove("usage");
        facts.remove("version");
        facts.remove("code");
        return Map.copyOf(facts);
    }

    private List<String> administrationLines(String code, Object... facts) {
        return render(net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation.administration(
                new net.maddkraft.maddprestige.core.admin.AdministrationException(
                        code, "INTERNAL", "INTERNAL", facts)));
    }

    private List<String> administrationVariantLines(
            String code,
            net.maddkraft.maddprestige.core.admin.AdministrationSemanticVariant variant,
            Object... facts) {
        return render(net.maddkraft.maddprestige.core.admin.presentation.SemanticPresentation.administration(
                new net.maddkraft.maddprestige.core.admin.AdministrationException(
                        code, variant, "INTERNAL", "INTERNAL", facts)));
    }

    private static Path repositoryRoot() {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("maddprestige-core/src/main/java"))) {
            root = root.getParent();
        }
        return java.util.Objects.requireNonNull(root, "repository root");
    }

    private List<String> render(
            List<net.maddkraft.maddprestige.core.admin.presentation.MessageReference> references) {
        return references.stream().map(messages::render).map(PaperMessageServiceTest::plain).toList();
    }

    private static String plain(net.kyori.adventure.text.Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
