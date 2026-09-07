package net.maddkraft.maddprestige.core.config.lifecycle;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.EnumMap;
import java.time.Instant;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.id.SeasonId;
import net.maddkraft.maddprestige.api.cost.CostDefinition;
import net.maddkraft.maddprestige.api.id.CostId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.core.command.CommandActionPolicy;
import net.maddkraft.maddprestige.core.competition.CompetitionConfiguration;
import net.maddkraft.maddprestige.core.config.progression.ProgressionConfiguration;
import net.maddkraft.maddprestige.core.provider.ProviderRegistry;
import net.maddkraft.maddprestige.core.rank.ReconciliationPolicy;
import net.maddkraft.maddprestige.core.requirement.TargetRounding;
import net.maddkraft.maddprestige.core.scaling.PrestigeScalingSegment;
import net.maddkraft.maddprestige.core.scaling.SegmentScalingMode;
import net.maddkraft.maddprestige.core.scaling.SegmentTransition;
import net.maddkraft.maddprestige.core.scaling.SegmentedScalingProfile;
import net.maddkraft.maddprestige.core.stage.StageConfiguration;
import net.maddkraft.maddprestige.core.stage.StageDefinition;
import net.maddkraft.maddprestige.core.stage.StageProjection;
import net.maddkraft.maddprestige.core.season.SeasonDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LifecycleConfigurationValidatorTest {
    @Test
    @DisplayName("Legacy stages are ignored while active providers fail closed before apply")
    void rejectsMissingStageAndProviderReferences() {
        StageId originId = new StageId("origin");
        StageId summitId = new StageId("summit");
        StageConfiguration stages = new StageConfiguration(2, true,
                Map.of(originId, new StageDefinition(originId, true, "Origin", Map.of(), StageProjection.none()),
                        summitId, new StageDefinition(summitId, false, "Summit", Map.of(), StageProjection.none())),
                List.of(originId), Optional.of(originId), ReconciliationPolicy.WARN_ONLY);
        CostId costId = new CostId("prestige_cost");
        ProviderId absentProvider = new ProviderId("absent_cost_provider");
        ProgressionConfiguration progression = new ProgressionConfiguration(3, 16, Map.of(), Map.of(),
                Map.of(costId, new CostDefinition(costId, absentProvider, "generic", MetricValue.count(1),
                        Map.of(), "Prestige cost")), Map.of(), CommandActionPolicy.safeDefaults());
        PrestigeConfiguration prestige = new PrestigeConfiguration(true, Set.of(summitId), originId, 1, 1,
                PrestigeLimit.unlimited(), Duration.ZERO, Optional.empty(), List.of(costId), List.of(),
                Optional.empty(), Optional.empty(), ResetPreservePolicy.safeDefaults(), false);
        LifecycleConfiguration lifecycle = new LifecycleConfiguration(4, prestige, Map.of(), Map.of(), Map.of(),
                Map.of(), CompetitionConfiguration.disabled());

        LifecycleProviderValidation validation = new LifecycleConfigurationValidator().validateProviders(
                lifecycle, progression, stages, new ProviderRegistry(), Map.of());

        assertTrue(validation.report().hasErrors());
        assertFalse(validation.report().findings().stream()
                .anyMatch(finding -> finding.code().contains("stage")));
        assertTrue(validation.report().findings().stream()
                .anyMatch(finding -> finding.code().equals("lifecycle.configuration.provider.unavailable")));
        assertTrue(validation.providerGenerations().isEmpty());
    }

    @Test
    void resetPolicyMatrixAcceptsOnlyTruthfulExecutableCombinations() {
        StageConfiguration stages = validStages();
        ProgressionConfiguration progression = ProgressionConfiguration.empty();
        LifecycleConfigurationValidator validator = new LifecycleConfigurationValidator();
        assertFalse(validator.validate(configuration(ResetPreservePolicy.safeDefaults(), Optional.empty(),
                Optional.empty(), Map.of()), progression, stages).hasErrors());

        EnumMap<ResetComponent, ResetDisposition> currencyPreserve = values();
        currencyPreserve.put(ResetComponent.PRESTIGE_SCOPED_CURRENCY, ResetDisposition.PRESERVE);
        assertFalse(validator.validate(configuration(new ResetPreservePolicy(currencyPreserve), Optional.empty(),
                Optional.empty(), Map.of()), progression, stages).hasErrors());

        EnumMap<ResetComponent, ResetDisposition> scopedPreserve = values();
        scopedPreserve.put(ResetComponent.ACTIVE_REQUIREMENT_PROGRESS, ResetDisposition.PRESERVE);
        scopedPreserve.put(ResetComponent.BASELINES, ResetDisposition.PRESERVE);
        scopedPreserve.put(ResetComponent.LATCHED_COMPLETIONS, ResetDisposition.PRESERVE);
        assertFalse(validator.validate(configuration(new ResetPreservePolicy(scopedPreserve), Optional.empty(),
                Optional.empty(), Map.of()), progression, stages).hasErrors());

        for (ResetComponent component : List.of(ResetComponent.PURCHASED_PERKS,
                ResetComponent.MILESTONE_HISTORY, ResetComponent.SEASON_PROGRESS)) {
            EnumMap<ResetComponent, ResetDisposition> unsupported = values();
            unsupported.put(component, ResetDisposition.RESET);
            assertTrue(validator.validate(configuration(new ResetPreservePolicy(unsupported), Optional.empty(),
                    Optional.empty(), Map.of()), progression, stages).hasErrors(), component.name());
        }
        EnumMap<ResetComponent, ResetDisposition> contradictory = values();
        contradictory.put(ResetComponent.BASELINES, ResetDisposition.PRESERVE);
        assertTrue(validator.validate(configuration(new ResetPreservePolicy(contradictory), Optional.empty(),
                Optional.empty(), Map.of()), progression, stages).hasErrors());

        EnumMap<ResetComponent, ResetDisposition> preserveStage = values();
        preserveStage.put(ResetComponent.PROGRESSION_STAGE, ResetDisposition.PRESERVE);
        prestige(new ResetPreservePolicy(preserveStage), Optional.empty(), Optional.empty());
        EnumMap<ResetComponent, ResetDisposition> resetHistory = values();
        resetHistory.put(ResetComponent.HISTORICAL_STATISTICS, ResetDisposition.RESET);
        assertThrows(IllegalArgumentException.class, () -> prestige(new ResetPreservePolicy(resetHistory),
                Optional.empty(), Optional.empty()));
    }

    @Test
    void inertProfileAndSeasonResolutionFieldsFailClosedWhileTimestampsRemainMetadata() {
        RequirementId source = new RequirementId("source");
        RequirementId target = new RequirementId("target");
        SeasonDefinition season = new SeasonDefinition(new SeasonId("chapter"), "Chapter",
                Optional.of(Instant.parse("2026-08-01T00:00:00Z")),
                Optional.of(Instant.parse("2026-09-01T00:00:00Z")), ResetDisposition.RESET,
                Map.of(source, target), Map.of(target, "catch-up"));
        var report = new LifecycleConfigurationValidator().validate(configuration(
                ResetPreservePolicy.safeDefaults(), Optional.of("scale"), Optional.of("catch-up"),
                Map.of(season.id(), season)), ProgressionConfiguration.empty(), validStages());

        assertTrue(report.hasErrors());
        assertTrue(report.findings().stream().anyMatch(finding ->
                finding.code().equals("prestige.configuration.profile.unsupported")));
        assertTrue(report.findings().stream().anyMatch(finding ->
                finding.code().equals("season.configuration.requirement_override.unsupported")));
        assertTrue(report.findings().stream().anyMatch(finding ->
                finding.code().equals("season.configuration.catch_up.unsupported")));

        PrestigeConfiguration disabledWithInertProfile = new PrestigeConfiguration(false, Set.of(),
                new StageId("origin"), 1, 1, PrestigeLimit.unlimited(), Duration.ZERO, Optional.empty(), List.of(),
                List.of(), Optional.of("still-inert"), Optional.empty(), ResetPreservePolicy.safeDefaults(), false);
        LifecycleConfiguration disabled = new LifecycleConfiguration(4, disabledWithInertProfile, Map.of(),
                Map.of(), Map.of(), Map.of(), CompetitionConfiguration.disabled());
        assertTrue(new LifecycleConfigurationValidator().validate(disabled, ProgressionConfiguration.empty(),
                validStages()).findings().stream().anyMatch(finding ->
                        finding.code().equals("prestige.configuration.profile.unsupported")));
    }

    @Test
    @DisplayName("Finite and open-ended coverage have exact findings")
    void finiteAndUnlimitedPrestigeRequireCompleteScalingCoverageAtPublication() {
        CostId costId = new CostId("scaled_cost");
        ProviderId providerId = new ProviderId("configured_provider");
        ProgressionConfiguration progression = new ProgressionConfiguration(3, 16, Map.of(), Map.of(),
                Map.of(costId, new CostDefinition(costId, providerId, "generic", MetricValue.count(1), Map.of(),
                        "Scaled cost")), Map.of(), CommandActionPolicy.safeDefaults());
        LifecycleConfigurationValidator validator = new LifecycleConfigurationValidator();

        assertTrue(validator.validate(scaled(costId, PrestigeLimit.finite(10), profile(OptionalLong.of(9))),
                progression, validStages()).findings().stream()
                .anyMatch(finding -> finding.code().equals("scaling.coverage.incomplete_finite")
                        && finding.explanation().contains("P10")));
        assertTrue(validator.validate(scaled(costId, PrestigeLimit.unlimited(), profile(OptionalLong.of(10))),
                progression, validStages()).findings().stream()
                .anyMatch(finding -> finding.code().equals("scaling.coverage.incomplete_unlimited")
                        && finding.explanation().contains("P1+")));
        assertFalse(validator.validate(scaled(costId, PrestigeLimit.finite(10), profile(OptionalLong.of(10))),
                progression, validStages()).findings().stream()
                .anyMatch(finding -> finding.code().startsWith("numeric-migration.scaling.incomplete_")));
        assertFalse(validator.validate(scaled(costId, PrestigeLimit.unlimited(), profile(OptionalLong.empty())),
                progression, validStages()).findings().stream()
                .anyMatch(finding -> finding.code().startsWith("numeric-migration.scaling.incomplete_")));
    }

    private static LifecycleConfiguration scaled(
            CostId costId, PrestigeLimit limit, SegmentedScalingProfile profile) {
        PrestigeConfiguration prestige = new PrestigeConfiguration(true, Set.of(), new StageId("compatibility"),
                1, 1, limit, Duration.ZERO, Optional.empty(), List.of(costId), List.of(), Optional.empty(),
                Optional.empty(), ResetPreservePolicy.safeDefaults(), false);
        return new LifecycleConfiguration(4, prestige, Map.of(), Map.of(), Map.of(), Map.of(),
                new PrestigeValueScalingConfiguration(Map.of(costId, profile), Map.of()),
                CompetitionConfiguration.disabled());
    }

    private static SegmentedScalingProfile profile(OptionalLong end) {
        return new SegmentedScalingProfile(List.of(new PrestigeScalingSegment(1, end, SegmentScalingMode.FLAT,
                SegmentTransition.EXPLICIT_BASE, net.maddkraft.maddprestige.api.value.ExactDecimal.parse("1"),
                net.maddkraft.maddprestige.api.value.ExactDecimal.ZERO, TargetRounding.EXACT,
                net.maddkraft.maddprestige.api.value.ExactDecimal.parse("1"), Optional.empty(), Optional.empty(),
                new TreeMap<>())));
    }

    private static EnumMap<ResetComponent, ResetDisposition> values() {
        return new EnumMap<>(ResetPreservePolicy.safeDefaults().dispositions());
    }

    private static LifecycleConfiguration configuration(
            ResetPreservePolicy policy,
            Optional<String> scaling,
            Optional<String> catchUp,
            Map<SeasonId, SeasonDefinition> seasons) {
        return new LifecycleConfiguration(4, prestige(policy, scaling, catchUp), Map.of(), Map.of(), Map.of(),
                seasons, CompetitionConfiguration.disabled());
    }

    private static PrestigeConfiguration prestige(
            ResetPreservePolicy policy,
            Optional<String> scaling,
            Optional<String> catchUp) {
        return new PrestigeConfiguration(true, Set.of(new StageId("summit")), new StageId("origin"), 1, 1,
                PrestigeLimit.unlimited(), Duration.ZERO, Optional.empty(), List.of(), List.of(), scaling, catchUp,
                policy, false);
    }

    private static StageConfiguration validStages() {
        StageId origin = new StageId("origin");
        StageId summit = new StageId("summit");
        return new StageConfiguration(2, true, Map.of(
                origin, new StageDefinition(origin, true, "Origin", Map.of(), StageProjection.none()),
                summit, new StageDefinition(summit, true, "Summit", Map.of(), StageProjection.none())),
                List.of(origin, summit), Optional.of(origin), ReconciliationPolicy.WARN_ONLY);
    }
}
