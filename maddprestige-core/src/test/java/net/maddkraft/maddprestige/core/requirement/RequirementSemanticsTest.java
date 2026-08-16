package net.maddkraft.maddprestige.core.requirement;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.metric.MetricOperator;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.value.ExactDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequirementSemanticsTest {
    private static final ProviderId PROVIDER = new ProviderId("provider");
    private static final MetricId METRIC = new MetricId("metric");

    @Test
    @DisplayName("Semantic fingerprint v2 is unambiguous for delimiters, newlines, and map shapes")
    void separatesPreviouslyAmbiguousFilterShapes() {
        String combined = fingerprint(RequirementTarget.single(MetricValue.decimal("1")), ScalingProfile.none(),
                CatchUpProfile.disabled(), Map.of("a", "b\nc=d"));
        String split = fingerprint(RequirementTarget.single(MetricValue.decimal("1")), ScalingProfile.none(),
                CatchUpProfile.disabled(), Map.of("a", "b", "c", "d"));
        assertNotEquals(combined, split);
        assertNotEquals(fingerprint(RequirementTarget.single(MetricValue.decimal("1")), ScalingProfile.none(),
                        CatchUpProfile.disabled(), Map.of("a=b", "c=d")),
                fingerprint(RequirementTarget.single(MetricValue.decimal("1")), ScalingProfile.none(),
                        CatchUpProfile.disabled(), Map.of("a", "b=c=d")));
        assertTrue(combined.startsWith("rsf2:"));
    }

    @Test
    @DisplayName("Every scaling, catch-up, rounding, and typed-target semantic changes the fingerprint")
    void coversEveryExplicitSemanticComponent() {
        ScalingProfile linear = ScalingProfile.linear("0.1", TargetRounding.CEILING);
        ScalingProfile differentRounding = new ScalingProfile(ScalingStrategy.LINEAR, ExactDecimal.parse("0.1"),
                new TreeMap<>(), TargetRounding.FLOOR, ExactDecimal.parse("1"));
        CatchUpProfile catchUp = new CatchUpProfile(true, ExactDecimal.parse("2"), ExactDecimal.parse("0.1"),
                ExactDecimal.parse("0.5"), Optional.of(ExactDecimal.parse("1")), TargetRounding.CEILING,
                ExactDecimal.parse("1"));
        String base = fingerprint(RequirementTarget.single(MetricValue.decimal("1")), ScalingProfile.none(),
                CatchUpProfile.disabled(), Map.of());
        assertNotEquals(base, fingerprint(RequirementTarget.single(MetricValue.decimal("1")), linear,
                CatchUpProfile.disabled(), Map.of()));
        assertNotEquals(fingerprint(RequirementTarget.single(MetricValue.decimal("1")), linear,
                        CatchUpProfile.disabled(), Map.of()),
                fingerprint(RequirementTarget.single(MetricValue.decimal("1")), differentRounding,
                        CatchUpProfile.disabled(), Map.of()));
        assertNotEquals(base, fingerprint(RequirementTarget.single(MetricValue.decimal("1")),
                ScalingProfile.none(), catchUp, Map.of()));
        assertNotEquals(base, fingerprint(RequirementTarget.single(
                MetricValue.parse(MetricValueType.STRING, "1")), ScalingProfile.none(),
                CatchUpProfile.disabled(), Map.of()));
        NavigableMap<Long, ExactDecimal> steps = new TreeMap<>();
        steps.put(2L, ExactDecimal.parse("3"));
        assertNotEquals(base, fingerprint(RequirementTarget.single(MetricValue.decimal("1")),
                new ScalingProfile(ScalingStrategy.STEPPED, ExactDecimal.ZERO, steps, TargetRounding.EXACT,
                        ExactDecimal.parse("1")), CatchUpProfile.disabled(), Map.of()));
    }

    @Test
    @DisplayName("Persistable definitions reject control characters and unbounded filter components")
    void rejectsUnsafePersistedFilterInputs() {
        assertThrows(IllegalArgumentException.class, () -> RequirementDefinition.create(
                new RequirementId("unsafe"), PROVIDER, METRIC, MetricOperator.GREATER_OR_EQUAL,
                RequirementTarget.single(MetricValue.decimal("1")), MeasurementScope.ABSOLUTE,
                CompletionMode.LIVE, ScalingProfile.none(), CatchUpProfile.disabled(), Map.of("a", "b\nc"),
                Map.of(), false));
        assertThrows(IllegalArgumentException.class, () -> RequirementDefinition.create(
                new RequirementId("long_filter"), PROVIDER, METRIC, MetricOperator.GREATER_OR_EQUAL,
                RequirementTarget.single(MetricValue.decimal("1")), MeasurementScope.ABSOLUTE,
                CompletionMode.LIVE, ScalingProfile.none(), CatchUpProfile.disabled(), Map.of("a", "x".repeat(513)),
                Map.of(), false));
    }

    private static String fingerprint(
            RequirementTarget target,
            ScalingProfile scaling,
            CatchUpProfile catchUp,
            Map<String, String> filters) {
        return RequirementSemantics.fingerprint(PROVIDER, METRIC, MetricOperator.GREATER_OR_EQUAL, target,
                MeasurementScope.ABSOLUTE, CompletionMode.LIVE, scaling, catchUp, filters);
    }
}
