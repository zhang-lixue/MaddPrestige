package net.maddkraft.maddprestige.core.config.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.metric.MetricDescriptor;
import net.maddkraft.maddprestige.api.metric.MetricMonotonicity;
import net.maddkraft.maddprestige.api.metric.MetricOperator;
import net.maddkraft.maddprestige.api.metric.MetricReadMode;
import net.maddkraft.maddprestige.api.metric.MetricResetPolicy;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.core.config.CompiledConfiguration;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.core.requirement.MetricBinding;
import net.maddkraft.maddprestige.core.requirement.CompletionMode;
import net.maddkraft.maddprestige.core.requirement.MeasurementScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProgressionConfigurationCompilerTest {
    private static final ProviderId PROVIDER = new ProviderId("provider");
    private static final MetricId METRIC = new MetricId("progress");
    private static final MetricDescriptor DESCRIPTOR = new MetricDescriptor(PROVIDER, METRIC, MetricValueType.COUNT,
            MetricOperator.compatibleWith(MetricValueType.COUNT),
            Set.of(MetricReadMode.CURRENT, MetricReadMode.LIFETIME), true, MetricMonotonicity.MONOTONIC,
            MetricResetPolicy.FAIL_RECONCILIATION, Map.of(), "Progress", "Progress", "count", "test");
    private static final MetricDescriptor INTEGER_DESCRIPTOR = new MetricDescriptor(PROVIDER, METRIC,
            MetricValueType.INTEGER, MetricOperator.compatibleWith(MetricValueType.INTEGER),
            Set.of(MetricReadMode.CURRENT), false, MetricMonotonicity.NON_MONOTONIC,
            MetricResetPolicy.FAIL_RECONCILIATION, Map.of(), "Total level", "Total level", "levels", "test");

    @Test
    @DisplayName("[A09-A26] Canonical requirements/rewards documents compile stable references and typed definitions")
    void compilesCanonicalProgressionDocuments() {
        var compilation = compile(validRequirements(), validRewards());
        assertFalse(compilation.validation().hasErrors(), compilation.validation().toString());
        assertEquals(1, compilation.configuration().requirements().size());
        assertEquals(1, compilation.configuration().trees().size());
        assertEquals(1, compilation.configuration().costs().size());
        assertEquals(1, compilation.configuration().rewards().size());
        assertFalse(compilation.configuration().commandPolicy().enabled());
    }

    @Test
    @DisplayName("[A09][A15][A55] Untyped dormant metrics and dangerous command templates fail apply")
    void rejectsInvalidCapabilitiesAndUnsafeCommands() {
        String requirements = validRequirements().replace("metric: progress", "metric: absent");
        String rewards = """
                schema-version: 3
                rewards: {}
                command-actions:
                  enabled: true
                  allowed-roots: [op]
                  blocked-roots: []
                  allowed-tokens: [player_name]
                  templates:
                    privilege:
                      command: op {player_name}
                      tokens: [player_name]
                  maximum-commands: 1
                  maximum-length: 64
                  maximum-depth: 0
                """;
        var report = compile(requirements, rewards).validation();
        assertTrue(report.hasErrors());
        assertTrue(report.findings().stream().anyMatch(finding ->
                finding.code().equals("requirement.value_type.required")));
        assertTrue(report.findings().stream().anyMatch(finding -> finding.code().equals("command.root.blocked")));
    }

    @Test
    @DisplayName("[A09][A49] Explicitly typed dormant definitions compile without activating absent providers")
    void compilesTypedDormantRequirementWithoutProvider() {
        String requirements = validRequirements()
                .replace("metric: progress", "metric: absent\n    value-type: count");
        Map<String, String> documents = Map.of("requirements.yml", requirements,
                "rewards.yml", validRewards());
        var compilation = new ProgressionConfigurationCompiler().compile(
                new CompiledConfiguration(RevisionHasher.hashDocuments(documents), documents), Map.of());

        assertFalse(compilation.validation().hasErrors(), compilation.validation().toString());
        assertEquals(MetricValueType.COUNT,
                compilation.configuration().requirements().get(new net.maddkraft.maddprestige.api.id.RequirementId(
                        "play")).target().lower().type());
    }

    @Test
    @DisplayName("Schema-canonical INTEGER_COUNT compiles to provider COUNT or INTEGER type")
    void compilesSchemaCanonicalIntegerCount() {
        String requirements = validRequirements().replace(
                "metric: progress", "metric: progress\n    value-type: INTEGER_COUNT");
        String integerRequirements = requirements.replace("scope: since-stage-start", "scope: absolute")
                .replace("completion: latched", "completion: live");

        var compilation = compile(requirements, validRewards());
        var integerCompilation = compile(integerRequirements, validRewards(), INTEGER_DESCRIPTOR);

        assertFalse(compilation.validation().hasErrors(), compilation.validation().toString());
        assertFalse(integerCompilation.validation().hasErrors(), integerCompilation.validation().toString());
        assertEquals(MetricValueType.COUNT,
                compilation.configuration().requirements().get(new net.maddkraft.maddprestige.api.id.RequirementId(
                        "play")).target().lower().type());
        assertEquals(MetricValueType.INTEGER,
                integerCompilation.configuration().requirements().get(
                        new net.maddkraft.maddprestige.api.id.RequirementId("play")).target().lower().type());
    }

    @Test
    @DisplayName("[A09] Invalid typed target parsing is owner-readable and never activates")
    void rejectsInvalidTypedTarget() {
        var report = compile(validRequirements().replace("target: 10", "target: ten"), validRewards()).validation();
        assertTrue(report.hasErrors());
        assertTrue(report.findings().stream().anyMatch(finding ->
                finding.code().equals("requirement.target.invalid")
                        && finding.path().contains("target")));
    }

    @Test
    @DisplayName("[A63] Unknown future requirement or reward schemas fail closed")
    void rejectsFutureDocumentSchemas() {
        var requirementFailure = compile(
                validRequirements().replace("schema-version: 3", "schema-version: 99"), validRewards());
        var rewardFailure = compile(
                validRequirements(), validRewards().replace("schema-version: 3", "schema-version: 99"));

        assertTrue(requirementFailure.validation().findings().stream()
                .anyMatch(finding -> finding.code().equals("progression.configuration.schema.version")));
        assertTrue(rewardFailure.validation().findings().stream()
                .anyMatch(finding -> finding.code().equals("progression.configuration.schema.version")));
    }

    @Test
    @DisplayName("Compact requirement scaling inherits safe defaults and remains deterministic")
    void compilesCompactRequirementScaling() {
        String requirements = validRequirements().replace(
                "scaling: {strategy: linear, rate: 0.1, rounding: ceiling}", """
                scaling:
                      mode: LINEAR
                      base: 1
                      rate: 0.5""");

        var compilation = compile(requirements, validRewards());

        assertFalse(compilation.validation().hasErrors(), compilation.validation().toString());
        var scaling = compilation.configuration().requirements()
                .get(new net.maddkraft.maddprestige.api.id.RequirementId("play")).scaling();
        assertEquals("1", scaling.multiplier(0).toPlainString());
        assertEquals("3", scaling.multiplier(4).toPlainString());
    }

    @Test
    @DisplayName("Sparse root ID is inferred and explicit mismatch fails clearly")
    void infersSparseRootIdAndRejectsMismatch() {
        var sparse = compile(validRequirements(), validRewards());
        assertFalse(sparse.validation().hasErrors(), sparse.validation().toString());
        assertTrue(sparse.configuration().trees().containsKey(
                new net.maddkraft.maddprestige.api.id.RequirementId("eligibility")));

        var mismatch = compile(validRequirements().replace("eligibility:\n    mode:",
                "eligibility:\n    id: another_root\n    mode:"), validRewards());
        assertTrue(mismatch.validation().findings().stream().anyMatch(finding ->
                finding.code().equals("requirement.tree.id")
                        && finding.explanation().contains("map key")));
    }

    @Test
    @DisplayName("Canonical scope and completion fields drive compiled behavior")
    void consumesCanonicalScopeAndCompletionFields() {
        var requirement = compile(validRequirements(), validRewards()).configuration().requirements()
                .get(new net.maddkraft.maddprestige.api.id.RequirementId("play"));

        assertEquals(MeasurementScope.SINCE_STAGE_START, requirement.scope());
        assertEquals(CompletionMode.LATCHED, requirement.completionMode());
    }

    private static ProgressionConfigurationCompilation compile(String requirements, String rewards) {
        return compile(requirements, rewards, DESCRIPTOR);
    }

    private static ProgressionConfigurationCompilation compile(
            String requirements,
            String rewards,
            MetricDescriptor descriptor) {
        Map<String, String> documents = Map.of("requirements.yml", requirements, "rewards.yml", rewards);
        return new ProgressionConfigurationCompiler().compile(
                new CompiledConfiguration(RevisionHasher.hashDocuments(documents), documents),
                Map.of(new MetricBinding(PROVIDER, METRIC), descriptor));
    }

    private static String validRequirements() {
        return """
                schema-version: 3
                maximum-depth: 8
                requirements:
                  play:
                    provider: provider
                    metric: progress
                    operator: greater-or-equal
                    target: 10
                    scope: since-stage-start
                    completion: latched
                    scaling: {strategy: linear, rate: 0.1, rounding: ceiling}
                    catch-up:
                      enabled: true
                      start-threshold: 2
                      reduction-rate: 0.05
                      maximum-reduction: 0.25
                      floor: 5
                      rounding: ceiling
                trees:
                  eligibility:
                    mode: all
                    children:
                      - requirement: play
                costs:
                  payment:
                    provider: provider
                    type: debit
                    value-type: currency-amount
                    amount: 25.50
                    display-name: Payment
                """;
    }

    private static String validRewards() {
        return """
                schema-version: 3
                rewards:
                  grant:
                    provider: provider
                    type: grant
                    value-type: exact-decimal
                    value: 1
                    display-name: Grant
                    failure-policy: required
                    repeatability: once-per-operation
                command-actions:
                  enabled: false
                  allowed-roots: []
                  blocked-roots: [stop, restart, op, deop]
                  allowed-tokens: []
                  templates: {}
                  maximum-commands: 5
                  maximum-length: 256
                  maximum-depth: 0
                """;
    }
}
