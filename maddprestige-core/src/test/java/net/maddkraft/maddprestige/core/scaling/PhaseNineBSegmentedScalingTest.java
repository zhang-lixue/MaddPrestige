package net.maddkraft.maddprestige.core.scaling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;
import net.maddkraft.maddprestige.api.cost.CostDefinition;
import net.maddkraft.maddprestige.api.id.CostId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RewardId;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.reward.RewardDefinition;
import net.maddkraft.maddprestige.api.reward.RewardFailurePolicy;
import net.maddkraft.maddprestige.api.reward.RewardRepeatability;
import net.maddkraft.maddprestige.api.value.ExactDecimal;
import net.maddkraft.maddprestige.core.config.phase3.PhaseThreeConfigurationCompiler;
import net.maddkraft.maddprestige.core.config.phase4.PrestigeValueScalingConfiguration;
import net.maddkraft.maddprestige.core.config.phase4.PrestigeLimit;
import net.maddkraft.maddprestige.core.requirement.TargetRounding;
import org.junit.jupiter.api.Test;

class PhaseNineBSegmentedScalingTest {
    @Test
    void flatLinearExponentialManualTransitionAndOverrideComposeDeterministically() {
        SegmentedScalingProfile profile = new SegmentedScalingProfile(List.of(
                segment(1, 2, SegmentScalingMode.FLAT, SegmentTransition.EXPLICIT_BASE,
                        "1", "0", Map.of(2L, "3")),
                segment(3, 4, SegmentScalingMode.LINEAR, SegmentTransition.CONTINUE,
                        "999", "1", Map.of()),
                segment(5, 6, SegmentScalingMode.EXPONENTIAL, SegmentTransition.EXPLICIT_BASE,
                        "2", "2", Map.of()),
                segment(7, 8, SegmentScalingMode.MANUAL, SegmentTransition.EXPLICIT_BASE,
                        "0", "0", Map.of(7L, "9", 8L, "11"))));

        assertEquals(ExactDecimal.parse("1"), profile.valueAt(1));
        assertEquals(ExactDecimal.parse("3"), profile.valueAt(2));
        assertEquals(ExactDecimal.parse("3"), profile.valueAt(3));
        assertEquals(ExactDecimal.parse("4"), profile.valueAt(4));
        assertEquals(ExactDecimal.parse("2"), profile.valueAt(5));
        assertEquals(ExactDecimal.parse("4"), profile.valueAt(6));
        assertEquals(ExactDecimal.parse("9"), profile.valueAt(7));
        assertEquals(ExactDecimal.parse("11"), profile.valueAt(8));
    }

    @Test
    void requirementCostAndRewardProfilesRemainIndependent() {
        CostId costId = new CostId("money");
        RewardId rewardId = new RewardId("tokens");
        SegmentedScalingProfile costScale = new SegmentedScalingProfile(List.of(segment(1, 10,
                SegmentScalingMode.LINEAR, SegmentTransition.EXPLICIT_BASE, "1", "1", Map.of())));
        SegmentedScalingProfile rewardScale = new SegmentedScalingProfile(List.of(segment(1, 10,
                SegmentScalingMode.FLAT, SegmentTransition.EXPLICIT_BASE, "3", "0", Map.of())));
        PrestigeValueScalingConfiguration scaling = new PrestigeValueScalingConfiguration(
                Map.of(costId, costScale), Map.of(rewardId, rewardScale));
        ProviderId provider = new ProviderId("configured_provider");
        CostDefinition cost = new CostDefinition(costId, provider, "currency", MetricValue.count(10),
                Map.of(), "Money");
        RewardDefinition reward = new RewardDefinition(rewardId, provider, "currency", MetricValue.count(2),
                Map.of(), "Tokens", RewardFailurePolicy.REQUIRED, RewardRepeatability.ONCE_PER_OPERATION);

        assertEquals("30", scaling.scale(cost, 3).amount().canonical());
        assertEquals("6", scaling.scale(reward, 3).value().canonical());
    }

    @Test
    void compilerAcceptsOpenEndedSegmentsAndRejectsGapsAndIncompleteManualRanges() {
        var parsed = PhaseThreeConfigurationCompiler.segmentedScaling(List.of(Map.of(
                "start-prestige", 1,
                "end-prestige", "unlimited",
                "mode", "LINEAR",
                "base", "2",
                "rate", "0.5")));
        assertEquals(ExactDecimal.parse("3"), parsed.valueAt(3));

        assertThrows(IllegalArgumentException.class, () -> new SegmentedScalingProfile(List.of(
                segment(1, 2, SegmentScalingMode.FLAT, SegmentTransition.EXPLICIT_BASE,
                        "1", "0", Map.of()),
                segment(4, 5, SegmentScalingMode.FLAT, SegmentTransition.EXPLICIT_BASE,
                        "1", "0", Map.of()))));
        assertThrows(IllegalArgumentException.class, () -> segment(1, 2, SegmentScalingMode.MANUAL,
                SegmentTransition.EXPLICIT_BASE, "0", "0", Map.of(1L, "4")));
    }

    @Test
    void openEndedFlatSegmentDoesNotImposeAnArtificialPrestigeMaximum() {
        SegmentedScalingProfile profile = new SegmentedScalingProfile(List.of(new PrestigeScalingSegment(
                1, OptionalLong.empty(), SegmentScalingMode.FLAT, SegmentTransition.EXPLICIT_BASE,
                ExactDecimal.parse("2"), ExactDecimal.ZERO, TargetRounding.EXACT, ExactDecimal.parse("1"),
                Optional.empty(), Optional.empty(), new TreeMap<>())));

        assertEquals(ExactDecimal.parse("2"), profile.valueAt(Long.MAX_VALUE));
        assertEquals(new java.math.BigDecimal("2"),
                net.maddkraft.maddprestige.core.requirement.ScalingProfile.segmented(profile.segments())
                        .multiplier(Long.MAX_VALUE - 1));
    }

    @Test
    void rejectsNegativeBoundsUnsafeInputsPostRoundOverflowOverlapAndRunawayGrowth() {
        assertThrows(IllegalArgumentException.class, () -> unsafeSegment("1", "0", "1",
                Optional.of(ExactDecimal.parse("-1")), Optional.empty(), TargetRounding.EXACT));
        assertThrows(IllegalArgumentException.class, () -> unsafeSegment("1", "0", "1",
                Optional.empty(), Optional.of(ExactDecimal.parse("-1")), TargetRounding.EXACT));
        assertThrows(IllegalArgumentException.class, () -> unsafeSegment("1E101", "0", "1",
                Optional.empty(), Optional.empty(), TargetRounding.EXACT));
        assertThrows(IllegalArgumentException.class, () -> unsafeSegment("1E-257", "0", "1",
                Optional.empty(), Optional.empty(), TargetRounding.EXACT));

        SegmentedScalingProfile postRoundOverflow = new SegmentedScalingProfile(List.of(unsafeSegment(
                "1E100", "0", "6E99", Optional.empty(), Optional.empty(), TargetRounding.CEILING)));
        assertThrows(IllegalArgumentException.class, () -> postRoundOverflow.valueAt(1));
        assertThrows(IllegalArgumentException.class, () -> new SegmentedScalingProfile(List.of(
                segment(1, 3, SegmentScalingMode.FLAT, SegmentTransition.EXPLICIT_BASE, "1", "0", Map.of()),
                segment(3, 4, SegmentScalingMode.FLAT, SegmentTransition.EXPLICIT_BASE, "1", "0", Map.of()))));
        SegmentedScalingProfile runaway = new SegmentedScalingProfile(List.of(segment(1, 1000,
                SegmentScalingMode.EXPONENTIAL, SegmentTransition.EXPLICIT_BASE, "1", "10", Map.of())));
        assertThrows(IllegalArgumentException.class, () -> runaway.valueAt(1000));
    }

    @Test
    void coverageMatchesFiniteAndUnlimitedPrestigePolicy() {
        SegmentedScalingProfile finite = new SegmentedScalingProfile(List.of(segment(1, 10,
                SegmentScalingMode.FLAT, SegmentTransition.EXPLICIT_BASE, "1", "0", Map.of())));
        SegmentedScalingProfile unlimited = new SegmentedScalingProfile(List.of(new PrestigeScalingSegment(
                1, OptionalLong.empty(), SegmentScalingMode.FLAT, SegmentTransition.EXPLICIT_BASE,
                ExactDecimal.parse("1"), ExactDecimal.ZERO, TargetRounding.EXACT, ExactDecimal.parse("1"),
                Optional.empty(), Optional.empty(), new TreeMap<>())));
        assertEquals(true, finite.covers(PrestigeLimit.finite(10)));
        assertEquals(false, finite.covers(PrestigeLimit.finite(11)));
        assertEquals(false, finite.covers(PrestigeLimit.unlimited()));
        assertEquals(true, unlimited.covers(PrestigeLimit.unlimited()));
    }

    private static PrestigeScalingSegment unsafeSegment(
            String base, String rate, String quantum, Optional<ExactDecimal> floor,
            Optional<ExactDecimal> cap, TargetRounding rounding) {
        return new PrestigeScalingSegment(1, OptionalLong.of(2), SegmentScalingMode.LINEAR,
                SegmentTransition.EXPLICIT_BASE, decimal(base), decimal(rate), rounding,
                decimal(quantum), floor, cap, new TreeMap<>());
    }

    private static ExactDecimal decimal(String value) {
        return value.indexOf('E') >= 0 ? ExactDecimal.of(new java.math.BigDecimal(value))
                : ExactDecimal.parse(value);
    }

    private static PrestigeScalingSegment segment(
            long start,
            long end,
            SegmentScalingMode mode,
            SegmentTransition transition,
            String base,
            String rate,
            Map<Long, String> overrides) {
        TreeMap<Long, ExactDecimal> values = new TreeMap<>();
        overrides.forEach((level, value) -> values.put(level, ExactDecimal.parse(value)));
        return new PrestigeScalingSegment(start, OptionalLong.of(end), mode, transition,
                ExactDecimal.parse(base), ExactDecimal.parse(rate), TargetRounding.EXACT, ExactDecimal.parse("1"),
                Optional.empty(), Optional.empty(), values);
    }
}
