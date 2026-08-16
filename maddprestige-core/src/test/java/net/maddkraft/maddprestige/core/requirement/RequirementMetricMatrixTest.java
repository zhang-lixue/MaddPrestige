package net.maddkraft.maddprestige.core.requirement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.RequirementId;
import net.maddkraft.maddprestige.api.id.ScopeId;
import net.maddkraft.maddprestige.api.metric.MetricDescriptor;
import net.maddkraft.maddprestige.api.metric.MetricMonotonicity;
import net.maddkraft.maddprestige.api.metric.MetricOperator;
import net.maddkraft.maddprestige.api.metric.MetricReadMode;
import net.maddkraft.maddprestige.api.metric.MetricResetPolicy;
import net.maddkraft.maddprestige.api.metric.MetricSample;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.value.ExactDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequirementMetricMatrixTest {
    private static final ProviderId PROVIDER = new ProviderId("typed-metrics");
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000321");
    private static final Instant NOW = Instant.parse("2026-08-15T00:00:00Z");

    @Test
    @DisplayName("[A09] Every metric family uses type-safe comparison, including inclusive ranges")
    void evaluatesEveryMetricFamilyAndRange() {
        assertSatisfied("integer", MetricValueType.INTEGER, MetricValue.integer(5),
                MetricOperator.GREATER_THAN, RequirementTarget.single(MetricValue.integer(4)));
        assertSatisfied("decimal", MetricValueType.EXACT_DECIMAL, MetricValue.decimal("1.10"),
                MetricOperator.EQUAL, RequirementTarget.single(MetricValue.decimal("1.1")));
        assertSatisfied("count", MetricValueType.COUNT, MetricValue.count(9),
                MetricOperator.LESS_OR_EQUAL, RequirementTarget.single(MetricValue.count(10)));
        assertSatisfied("duration", MetricValueType.DURATION, MetricValue.parse(MetricValueType.DURATION, "2h"),
                MetricOperator.GREATER_OR_EQUAL,
                RequirementTarget.single(MetricValue.parse(MetricValueType.DURATION, "90m")));
        assertSatisfied("boolean", MetricValueType.BOOLEAN, MetricValue.bool(true), MetricOperator.EQUAL,
                RequirementTarget.single(MetricValue.bool(true)));
        assertSatisfied("enum", MetricValueType.ENUM, MetricValue.parse(MetricValueType.ENUM, "epic"),
                MetricOperator.EQUAL, RequirementTarget.single(MetricValue.parse(MetricValueType.ENUM, "epic")));
        assertSatisfied("string", MetricValueType.STRING, MetricValue.parse(MetricValueType.STRING, "alpha"),
                MetricOperator.NOT_EQUAL,
                RequirementTarget.single(MetricValue.parse(MetricValueType.STRING, "beta")));
        assertSatisfied("range", MetricValueType.COUNT, MetricValue.count(5), MetricOperator.IN_RANGE,
                RequirementTarget.range(MetricValue.count(1), MetricValue.count(5)));
    }

    @Test
    @DisplayName("[A09][A15] Unsupported lifetime/delta capabilities are distinct validation failures")
    void rejectsUnsupportedReadScopeAndDelta() {
        MetricId metricId = new MetricId("current-only");
        MetricDescriptor descriptor = descriptor(metricId, MetricValueType.COUNT, Set.of(MetricReadMode.CURRENT),
                false);
        RequirementDefinition lifetime = definition("lifetime", metricId, MetricOperator.GREATER_OR_EQUAL,
                RequirementTarget.single(MetricValue.count(1)), MeasurementScope.LIFETIME);
        RequirementDefinition delta = definition("delta", metricId, MetricOperator.GREATER_OR_EQUAL,
                RequirementTarget.single(MetricValue.count(1)), MeasurementScope.SINCE_STAGE_START);
        RequirementTreeValidator validator = new RequirementTreeValidator();

        var lifetimeReport = validator.validate(new RequirementLeaf(lifetime),
                Map.of(new MetricBinding(PROVIDER, metricId), descriptor), 4);
        var deltaReport = validator.validate(new RequirementLeaf(delta),
                Map.of(new MetricBinding(PROVIDER, metricId), descriptor), 4);
        assertTrue(lifetimeReport.findings().stream().anyMatch(finding ->
                finding.code().equals("requirement.scope.unsupported")));
        assertTrue(deltaReport.findings().stream().anyMatch(finding ->
                finding.code().equals("requirement.scope.delta_unsupported")));
    }

    private static void assertSatisfied(
            String id,
            MetricValueType type,
            MetricValue current,
            MetricOperator operator,
            RequirementTarget target) {
        MetricId metricId = new MetricId(id);
        RequirementDefinition definition = definition(id, metricId, operator, target, MeasurementScope.ABSOLUTE);
        MetricDescriptor descriptor = descriptor(metricId, type,
                Set.of(MetricReadMode.CURRENT, MetricReadMode.LIFETIME), true);
        RequirementEvaluationContext context = new RequirementEvaluationContext(PLAYER,
                new ConfigRevisionId("typed_revision"), Map.of(PROVIDER, 1L), 0, ExactDecimal.ZERO,
                new ScopeContext(Map.of(MeasurementScope.ABSOLUTE, new ScopeId("absolute"))),
                Map.of(definition.id(), MetricSample.available(current, 1, NOW, "typed test")), emptyState());
        assertEquals(RequirementEvaluationStatus.SATISFIED,
                new RequirementEvaluator(Map.of(new MetricBinding(PROVIDER, metricId), descriptor))
                        .evaluate(new RequirementLeaf(definition), context).status(), id);
    }

    private static RequirementDefinition definition(
            String id,
            MetricId metricId,
            MetricOperator operator,
            RequirementTarget target,
            MeasurementScope scope) {
        return RequirementDefinition.create(new RequirementId(id), PROVIDER, metricId, operator, target, scope,
                CompletionMode.LIVE, ScalingProfile.none(), CatchUpProfile.disabled(), Map.of(), Map.of(), false);
    }

    private static MetricDescriptor descriptor(
            MetricId metricId,
            MetricValueType type,
            Set<MetricReadMode> reads,
            boolean delta) {
        return new MetricDescriptor(PROVIDER, metricId, type, MetricOperator.compatibleWith(type), reads, delta,
                MetricMonotonicity.NON_MONOTONIC, MetricResetPolicy.NOT_APPLICABLE, Map.of(), metricId.value(),
                "Typed metric test", "", "test");
    }

    private static RequirementStateReader emptyState() {
        return new RequirementStateReader() {
            @Override
            public Optional<RequirementBaseline> findBaseline(BaselineKey key) {
                return Optional.empty();
            }

            @Override
            public Optional<RequirementLatch> findLatch(LatchKey key) {
                return Optional.empty();
            }
        };
    }
}
