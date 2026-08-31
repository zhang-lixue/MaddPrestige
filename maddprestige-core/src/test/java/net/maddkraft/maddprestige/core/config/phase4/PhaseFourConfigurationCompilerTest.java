package net.maddkraft.maddprestige.core.config.phase4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.time.Duration;
import net.maddkraft.maddprestige.api.id.CostId;
import net.maddkraft.maddprestige.api.id.CurrencyId;
import net.maddkraft.maddprestige.api.id.EntitlementId;
import net.maddkraft.maddprestige.api.id.MilestoneId;
import net.maddkraft.maddprestige.api.id.SeasonId;
import net.maddkraft.maddprestige.api.id.RewardId;
import net.maddkraft.maddprestige.api.value.ExactDecimal;
import net.maddkraft.maddprestige.core.config.CompiledConfiguration;
import net.maddkraft.maddprestige.core.config.RevisionHasher;
import net.maddkraft.maddprestige.core.entitlement.EntitlementMergeStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhaseFourConfigurationCompilerTest {
    @Test
    @DisplayName("[A27-A34] Canonical lifecycle document compiles generic Phase 4 models")
    void compilesCanonicalLifecycleDocument() {
        PhaseFourConfigurationCompilation compilation = compile(validLifecycle());

        assertFalse(compilation.validation().hasErrors(), compilation.validation().toString());
        assertTrue(compilation.configuration().prestige().enabled());
        assertEquals(Duration.ofHours(12),
                compilation.configuration().prestige().confirmationMaximumLifetime());
        assertEquals(ExactDecimal.parse("3"), compilation.configuration().valueScaling().costs()
                .get(new CostId("prestige_cost")).valueAt(3));
        assertEquals(ExactDecimal.parse("2"), compilation.configuration().valueScaling().rewards()
                .get(new RewardId("prestige_reward")).valueAt(3));
        assertEquals(2, compilation.configuration().prestige().requiredStages().size());
        assertEquals(2, compilation.configuration().currencies().get(new CurrencyId("credits")).scale());
        assertEquals(EntitlementMergeStrategy.MAX, compilation.configuration().entitlements()
                .get(new EntitlementId("capacity")).strategy());
        assertTrue(compilation.configuration().milestones().containsKey(new MilestoneId("fifth")));
        assertTrue(compilation.configuration().seasons().containsKey(new SeasonId("chapter_one")));
        assertEquals(ResetDisposition.PRESERVE, compilation.configuration().seasons()
                .get(new SeasonId("chapter_one")).progressPolicy());
        assertFalse(compilation.configuration().competition().enabled());
    }

    @Test
    @DisplayName("[Phase 9D UX] Confirmation safety cap is configurable but cannot recreate a short timer")
    void confirmationMaximumLifetimeIsLongAndBounded() {
        String configured = validLifecycle().replace("  cooldown: PT1H\n",
                "  cooldown: PT1H\n  confirmation-maximum-lifetime: PT6H\n");
        PhaseFourConfigurationCompilation sixHours = compile(configured);
        assertFalse(sixHours.validation().hasErrors(), sixHours.validation().toString());
        assertEquals(Duration.ofHours(6), sixHours.configuration().prestige().confirmationMaximumLifetime());

        PhaseFourConfigurationCompilation tooShort = compile(configured.replace("PT6H", "PT30M"));
        assertTrue(tooShort.validation().findings().stream().anyMatch(finding ->
                finding.code().equals("phase4.prestige.invalid")));
    }

    @Test
    @DisplayName("[A33] Season policy compiles only the truthful season-progress surface")
    void seasonResetPolicyRejectsInertComponentsAndInvalidValues() {
        String preserveBlock = "    reset-policy:\n      season-progress: PRESERVE\n";
        String resetBlock = "    reset-policy:\n      season-progress: RESET\n";
        PhaseFourConfigurationCompilation reset = compile(validLifecycle().replace(preserveBlock, resetBlock));
        assertFalse(reset.validation().hasErrors(), reset.validation().toString());
        assertEquals(ResetDisposition.RESET,
                reset.configuration().seasons().get(new SeasonId("chapter_one")).progressPolicy());

        String unsupportedBlock = "    reset-policy:\n      season-progress: PRESERVE\n"
                + "      progression-stage: RESET\n";
        PhaseFourConfigurationCompilation unsupported = compile(
                validLifecycle().replace(preserveBlock, unsupportedBlock));
        assertTrue(unsupported.validation().findings().stream().anyMatch(finding ->
                finding.code().equals("phase4.season.reset_policy.unsupported_component")
                        && finding.path().endsWith("progression-stage")));

        String invalidBlock = "    reset-policy:\n      season-progress: SOMETIMES\n";
        PhaseFourConfigurationCompilation invalid = compile(validLifecycle().replace(preserveBlock, invalidBlock));
        assertTrue(invalid.validation().findings().stream().anyMatch(finding ->
                finding.code().equals("phase4.enum.invalid")
                        && finding.path().endsWith("season-progress")));
    }

    @Test
    @DisplayName("[A28][A30][A32][A34] Invalid lifecycle types and unsupported competition fail closed")
    void rejectsUnsafeOrAmbiguousLifecycleConfiguration() {
        String invalid = """
                schema-version: 3
                prestige:
                  enabled: true
                  required-stages: []
                  reset-stage: start
                  current-count-increment: 0
                  lifetime-count-increment: 1
                  maximum: zero
                  cooldown: -1
                  reset-policy:
                    progression-stage: PRESERVE
                currencies:
                  broken:
                    display-name: Broken
                    scale: 20
                    maximum-precision: 2
                    maximum-balance: 1
                entitlements:
                  flag:
                    value-type: boolean
                    merge-strategy: sum
                milestones: {}
                seasons: {}
                competition:
                  enabled: true
                """;
        PhaseFourConfigurationCompilation compilation = compile(invalid);

        assertTrue(compilation.validation().hasErrors());
        assertFalse(compilation.configuration().prestige().enabled());
        assertFalse(compilation.configuration().competition().enabled());
        assertTrue(compilation.validation().findings().stream()
                .anyMatch(finding -> finding.code().equals("phase4.competition.unsupported")));
        assertTrue(compilation.validation().findings().stream()
                .noneMatch(finding -> finding.code().equals("phase4.reset_policy.missing")));
    }

    @Test
    @DisplayName("[Phase 9C] Omitted lifecycle settings inherit safe defaults")
    void inheritsSafeLifecycleDefaults() {
        PhaseFourConfigurationCompilation compilation = compile("""
                schema-version: 4
                prestige:
                  enabled: true
                """);

        assertFalse(compilation.validation().hasErrors(), compilation.validation().toString());
        assertEquals(ResetDisposition.RESET, compilation.configuration().prestige().resetPolicy()
                .disposition(ResetComponent.ACTIVE_REQUIREMENT_PROGRESS));
        assertEquals(ResetDisposition.PRESERVE, compilation.configuration().prestige().resetPolicy()
                .disposition(ResetComponent.PURCHASED_PERKS));
    }

    @Test
    @DisplayName("[Phase 9C] Compact and inherited advanced cost/reward scaling resolve identically")
    void compilesCompactAndInheritedValueScaling() {
        PhaseFourConfigurationCompilation compilation = compile("""
                schema-version: 4
                prestige:
                  enabled: true
                  cost-scaling:
                    payment:
                      mode: LINEAR
                      base: 1
                      rate: 1
                  reward-scaling:
                    grant:
                      defaults:
                        rounding: EXACT
                      segments:
                        - end-prestige: 2
                          mode: FLAT
                          base: 2
                        - mode: LINEAR
                          transition: CONTINUE
                          rate: 1
                          overrides:
                            4: 9
                """);

        assertFalse(compilation.validation().hasErrors(), compilation.validation().toString());
        assertEquals(ExactDecimal.parse("3"), compilation.configuration().valueScaling().costs()
                .get(new CostId("payment")).valueAt(3));
        assertEquals(ExactDecimal.parse("2"), compilation.configuration().valueScaling().rewards()
                .get(new RewardId("grant")).valueAt(3));
        assertEquals(ExactDecimal.parse("9"), compilation.configuration().valueScaling().rewards()
                .get(new RewardId("grant")).valueAt(4));
    }

    @Test
    @DisplayName("[Phase 9C] Ambiguous inferred scaling boundaries fail closed")
    void rejectsAmbiguousInheritedScalingBoundaries() {
        PhaseFourConfigurationCompilation compilation = compile("""
                schema-version: 4
                prestige:
                  enabled: true
                  cost-scaling:
                    payment:
                      segments:
                        - mode: FLAT
                          base: 1
                        - mode: LINEAR
                          rate: 1
                """);

        assertTrue(compilation.validation().hasErrors());
        assertTrue(compilation.validation().findings().stream()
                .anyMatch(finding -> finding.code().equals("phase4.scaling.ambiguous_boundary")
                        && finding.explanation().contains("end-prestige")));
        assertTrue(compilation.configuration().valueScaling().costs().isEmpty());
    }

    @Test
    @DisplayName("[Phase 9C correction] Scaling gaps and overlaps have distinct actionable findings")
    void classifiesScalingGapsAndOverlaps() {
        PhaseFourConfigurationCompilation gap = compile(scalingLifecycle("""
                - start-prestige: 1
                  end-prestige: 10
                  mode: FLAT
                  base: 1
                - start-prestige: 12
                  end-prestige: unlimited
                  mode: FLAT
                  base: 2
                """));
        PhaseFourConfigurationCompilation overlap = compile(scalingLifecycle("""
                - start-prestige: 1
                  end-prestige: 20
                  mode: FLAT
                  base: 1
                - start-prestige: 15
                  end-prestige: unlimited
                  mode: FLAT
                  base: 2
                """));

        assertTrue(gap.validation().findings().stream().anyMatch(finding ->
                finding.code().equals("phase4.scaling.gap")
                        && finding.explanation().contains("P11")));
        assertTrue(overlap.validation().findings().stream().anyMatch(finding ->
                finding.code().equals("phase4.scaling.overlap")
                        && finding.explanation().contains("overlaps")));
    }

    @Test
    @DisplayName("[Phase 9C correction] Invalid ranges, bounds, and overrides retain exact categories")
    void classifiesInvalidScalingRangeBoundsAndOverride() {
        PhaseFourConfigurationCompilation range = compile(scalingLifecycle("""
                - start-prestige: 10
                  end-prestige: 5
                  mode: FLAT
                  base: 1
                """));
        PhaseFourConfigurationCompilation bounds = compile(scalingLifecycle("""
                - start-prestige: 1
                  end-prestige: unlimited
                  mode: FLAT
                  base: 1
                  floor: 10
                  cap: 5
                """));
        PhaseFourConfigurationCompilation override = compile(scalingLifecycle("""
                - start-prestige: 1
                  end-prestige: 10
                  mode: FLAT
                  base: 1
                  overrides:
                    25: 3
                """));

        assertTrue(hasFinding(range, "phase4.scaling.invalid_range"));
        assertTrue(hasFinding(bounds, "phase4.scaling.invalid_bounds"));
        assertTrue(hasFinding(override, "phase4.scaling.invalid_override"));
    }

    private static boolean hasFinding(PhaseFourConfigurationCompilation compilation, String code) {
        return compilation.validation().findings().stream().anyMatch(finding -> finding.code().equals(code));
    }

    private static String scalingLifecycle(String segments) {
        return "schema-version: 4\nprestige:\n  enabled: true\n  cost-scaling:\n    payment:\n"
                + "      segments:\n" + segments.indent(8);
    }

    private static PhaseFourConfigurationCompilation compile(String lifecycle) {
        Map<String, String> documents = Map.of("lifecycle.yml", lifecycle);
        return new PhaseFourConfigurationCompiler().compile(
                new CompiledConfiguration(RevisionHasher.hashDocuments(documents), documents));
    }

    private static String validLifecycle() {
        return """
                schema-version: 4
                prestige:
                  enabled: true
                  required-stages: [summit_a, summit_b]
                  reset-stage: start
                  current-count-increment: 1
                  lifetime-count-increment: 1
                  maximum: 25
                  cooldown: PT1H
                  requirement-tree: prestige_gate
                  costs: [prestige_cost]
                  rewards: [prestige_reward]
                  cost-scaling:
                    prestige_cost:
                      segments:
                        - start-prestige: 1
                          end-prestige: unlimited
                          mode: LINEAR
                          base: 1
                          rate: 1
                  reward-scaling:
                    prestige_reward:
                      segments:
                        - start-prestige: 1
                          end-prestige: unlimited
                          mode: FLAT
                          base: 2
                  external-resets:
                    enabled: false
                  reset-policy:
                    progression-stage: RESET
                    active-requirement-progress: RESET
                    latched-completions: RESET
                    baselines: RESET
                    prestige-scoped-currency: RESET
                    purchased-perks: PRESERVE
                    milestone-history: PRESERVE
                    season-progress: PRESERVE
                    historical-statistics: PRESERVE
                currencies:
                  credits:
                    display-name: Credits
                    symbol: C
                    scale: 2
                    rounding-mode: UNNECESSARY
                    maximum-precision: 8
                    maximum-balance: 999999.99
                    prestige-scoped: true
                entitlements:
                  capacity:
                    value-type: integer
                    merge-strategy: max
                milestones:
                  fifth:
                    display-name: Fifth
                    enabled: true
                    trigger: current-prestige
                    value-type: count
                    threshold: 5
                    repeatability: once
                    rewards: [prestige_reward]
                seasons:
                  chapter_one:
                    display-name: Chapter One
                    starts-at: 2026-08-15T00:00:00Z
                    ends-at: 2026-09-15T00:00:00Z
                    reset-policy:
                      season-progress: PRESERVE
                    requirement-overrides:
                      ordinary: seasonal
                    catch-up-profiles:
                      seasonal: trailing_players
                competition:
                  enabled: false
                """;
    }
}
