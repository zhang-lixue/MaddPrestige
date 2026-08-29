package net.maddkraft.maddprestige.core.requirement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
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

class RequirementEngineTest {
    private static final ProviderId PROVIDER = new ProviderId("metrics");
    private static final MetricId METRIC = new MetricId("count");
    private static final MetricBinding BINDING = new MetricBinding(PROVIDER, METRIC);
    private static final MetricDescriptor DESCRIPTOR = new MetricDescriptor(PROVIDER, METRIC,
            MetricValueType.COUNT, MetricOperator.compatibleWith(MetricValueType.COUNT),
            Set.of(MetricReadMode.CURRENT, MetricReadMode.LIFETIME), true, MetricMonotonicity.MONOTONIC,
            MetricResetPolicy.FAIL_RECONCILIATION, Map.of(), "Count", "Test count", "count", "authoritative");
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000123");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-15T12:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("[A09-A12] ALL, ANY, ANY_X, weighted, and nested trees preserve deterministic explanations")
    void evaluatesRecursiveTrees() {
        RequirementLeaf passedOne = leaf("passed_one", "10");
        RequirementLeaf passedTwo = leaf("passed_two", "10");
        RequirementLeaf failed = leaf("failed", "20");
        RequirementLeaf unavailable = leaf("unavailable", "1");
        Map<RequirementId, MetricSample> samples = samples(Map.of(
                passedOne.id(), MetricValue.count(10),
                passedTwo.id(), MetricValue.count(11),
                failed.id(), MetricValue.count(5)));
        samples = new LinkedHashMap<>(samples);
        samples.put(unavailable.id(), MetricSample.unavailable(1, CLOCK.instant(), "test", "outage"));
        RequirementEvaluator evaluator = evaluator();

        var all = RequirementGroup.all(new RequirementId("all"), List.of(
                RequirementChild.unweighted(passedOne), RequirementChild.unweighted(passedTwo)));
        assertEquals(RequirementEvaluationStatus.SATISFIED, evaluator.evaluate(all, context(samples)).status());

        var any = new RequirementGroup(new RequirementId("any"), RequirementGroupMode.ANY,
                List.of(RequirementChild.unweighted(failed), RequirementChild.unweighted(passedOne)),
                ExactDecimal.parse("1"), CatchUpProfile.disabled(), "Any");
        assertEquals(RequirementEvaluationStatus.SATISFIED, evaluator.evaluate(any, context(samples)).status());

        var anyTwo = new RequirementGroup(new RequirementId("any_two"), RequirementGroupMode.X_OF_N,
                List.of(RequirementChild.unweighted(passedOne), RequirementChild.unweighted(passedTwo),
                        RequirementChild.unweighted(failed), RequirementChild.unweighted(unavailable)),
                ExactDecimal.parse("2"), CatchUpProfile.disabled(), "Any two");
        var anyTwoResult = evaluator.evaluate(anyTwo, context(samples));
        assertEquals(RequirementEvaluationStatus.SATISFIED, anyTwoResult.status());
        assertEquals(4, anyTwoResult.explanation().children().size());

        var weighted = new RequirementGroup(new RequirementId("weighted"), RequirementGroupMode.WEIGHTED,
                List.of(new RequirementChild(passedOne, ExactDecimal.parse("2")),
                        new RequirementChild(passedTwo, ExactDecimal.parse("3")),
                        new RequirementChild(failed, ExactDecimal.parse("4"))),
                ExactDecimal.parse("5"), CatchUpProfile.disabled(), "Points");
        assertEquals(RequirementEvaluationStatus.SATISFIED,
                evaluator.evaluate(weighted, context(samples)).status());

        var nested = RequirementGroup.all(new RequirementId("nested"), List.of(
                RequirementChild.unweighted(all), RequirementChild.unweighted(weighted)));
        var nestedResult = evaluator.evaluate(nested, context(samples));
        assertTrue(nestedResult.satisfied());
        assertEquals("ALL", nestedResult.explanation().facts().get("mode"));
    }

    @Test
    @DisplayName("[A09-A12] Unavailable children fail closed whenever qualification depends on them")
    void propagatesUnavailabilityWithoutTreatingItAsFalseOrZero() {
        RequirementLeaf failed = leaf("failed_only", "20");
        RequirementLeaf unavailable = leaf("outage", "1");
        Map<RequirementId, MetricSample> samples = new LinkedHashMap<>(samples(Map.of(
                failed.id(), MetricValue.count(0))));
        samples.put(unavailable.id(), MetricSample.unavailable(1, CLOCK.instant(), "test", "provider down"));
        var any = new RequirementGroup(new RequirementId("any_outage"), RequirementGroupMode.ANY,
                List.of(RequirementChild.unweighted(failed), RequirementChild.unweighted(unavailable)),
                ExactDecimal.parse("1"), CatchUpProfile.disabled(), "Any outage");
        var weighted = new RequirementGroup(new RequirementId("weighted_outage"), RequirementGroupMode.WEIGHTED,
                List.of(new RequirementChild(failed, ExactDecimal.parse("1")),
                        new RequirementChild(unavailable, ExactDecimal.parse("5"))),
                ExactDecimal.parse("5"), CatchUpProfile.disabled(), "Weighted outage");
        assertEquals(RequirementEvaluationStatus.UNAVAILABLE, evaluator().evaluate(any, context(samples)).status());
        assertEquals(RequirementEvaluationStatus.UNAVAILABLE,
                evaluator().evaluate(weighted, context(samples)).status());
    }

    @Test
    @DisplayName("[A12] Mixed availability truth tables return known decisions and retain every explanation")
    void evaluatesDecisionAwareMixedStatusTruthTables() {
        RequirementLeaf satisfied = leaf("truth_satisfied", "1");
        RequirementLeaf failed = leaf("truth_failed", "1");
        RequirementLeaf unavailable = leaf("truth_unavailable", "1");
        Map<RequirementId, MetricSample> values = new LinkedHashMap<>(samples(Map.of(
                satisfied.id(), MetricValue.count(1), failed.id(), MetricValue.count(0))));
        values.put(unavailable.id(), MetricSample.unavailable(1, CLOCK.instant(), "test", "outage"));

        assertGroup(RequirementGroupMode.ALL, "all_known_false", "1",
                List.of(child(failed, "1"), child(unavailable, "1")), values,
                RequirementEvaluationStatus.UNSATISFIED);
        assertGroup(RequirementGroupMode.ALL, "all_needs_unknown", "1",
                List.of(child(satisfied, "1"), child(unavailable, "1")), values,
                RequirementEvaluationStatus.UNAVAILABLE);
        assertGroup(RequirementGroupMode.ANY, "any_known_true", "1",
                List.of(child(satisfied, "1"), child(unavailable, "1")), values,
                RequirementEvaluationStatus.SATISFIED);
        assertGroup(RequirementGroupMode.ANY, "any_needs_unknown", "1",
                List.of(child(failed, "1"), child(unavailable, "1")), values,
                RequirementEvaluationStatus.UNAVAILABLE);
        assertGroup(RequirementGroupMode.ANY_X_OF_Y, "any_x_needs_unknown", "2",
                List.of(child(satisfied, "1"), child(failed, "1"), child(unavailable, "1")), values,
                RequirementEvaluationStatus.UNAVAILABLE);
        assertGroup(RequirementGroupMode.WEIGHTED, "weighted_impossible", "5",
                List.of(child(satisfied, "2"), child(failed, "4"), child(unavailable, "2")), values,
                RequirementEvaluationStatus.UNSATISFIED);
        assertGroup(RequirementGroupMode.WEIGHTED, "weighted_needs_unknown", "5",
                List.of(child(satisfied, "2"), child(failed, "1"), child(unavailable, "3")), values,
                RequirementEvaluationStatus.UNAVAILABLE);
    }

    @Test
    @DisplayName("[A09-A12] Group boundaries cover incomplete ALL/ANY, exact ANY_X, and weighted thresholds")
    void evaluatesEveryGroupBoundary() {
        RequirementLeaf first = leaf("boundary_first", "10");
        RequirementLeaf second = leaf("boundary_second", "10");
        RequirementLeaf failed = leaf("boundary_failed", "10");
        Map<RequirementId, MetricSample> samples = samples(Map.of(
                first.id(), MetricValue.count(10), second.id(), MetricValue.count(10),
                failed.id(), MetricValue.count(0)));
        RequirementEvaluator evaluator = evaluator();

        RequirementGroup all = RequirementGroup.all(new RequirementId("boundary_all"), List.of(
                RequirementChild.unweighted(first), RequirementChild.unweighted(failed)));
        RequirementGroup any = new RequirementGroup(new RequirementId("boundary_any"), RequirementGroupMode.ANY,
                List.of(RequirementChild.unweighted(failed)), ExactDecimal.parse("1"),
                CatchUpProfile.disabled(), "Any");
        RequirementGroup anyTwo = new RequirementGroup(new RequirementId("boundary_any_two"),
                RequirementGroupMode.ANY_X_OF_Y, List.of(RequirementChild.unweighted(first),
                        RequirementChild.unweighted(second), RequirementChild.unweighted(failed)),
                ExactDecimal.parse("2"), CatchUpProfile.disabled(), "Any two");
        assertEquals(RequirementEvaluationStatus.UNSATISFIED, evaluator.evaluate(all, context(samples)).status());
        assertEquals(RequirementEvaluationStatus.UNSATISFIED, evaluator.evaluate(any, context(samples)).status());
        assertEquals(RequirementEvaluationStatus.SATISFIED,
                evaluator.evaluate(anyTwo, context(samples)).status(), "exact ANY_X boundary");

        for (String threshold : List.of("4", "5", "6")) {
            RequirementGroup weighted = new RequirementGroup(new RequirementId("weighted_" + threshold),
                    RequirementGroupMode.WEIGHTED, List.of(
                            new RequirementChild(first, ExactDecimal.parse("2")),
                            new RequirementChild(second, ExactDecimal.parse("3")),
                            new RequirementChild(failed, ExactDecimal.parse("4"))),
                    ExactDecimal.parse(threshold), CatchUpProfile.disabled(), "Weighted");
            RequirementEvaluationStatus expected = "6".equals(threshold)
                    ? RequirementEvaluationStatus.UNSATISFIED : RequirementEvaluationStatus.SATISFIED;
            var result = evaluator.evaluate(weighted, context(samples));
            assertEquals(expected, result.status(), "weighted threshold " + threshold);
            assertEquals(result.explanation(), evaluator.evaluate(weighted, context(samples)).explanation(),
                    "explanation must be deterministic");
        }
    }

    @Test
    @DisplayName("[A13-A17] Live, latched, stage, synthetic prestige, and synthetic season scopes isolate state")
    void evaluatesScopesAndPersistentLatchTransitions() {
        InMemoryStates states = new InMemoryStates();
        ScopeContext scopes = scopes("cycle_a");
        RequirementLeaf live = leaf("live", "10", MeasurementScope.ABSOLUTE, CompletionMode.LIVE);
        RequirementLeaf latched = leaf("latched", "10", MeasurementScope.ABSOLUTE, CompletionMode.LATCHED);
        RequirementLeaf stage = leaf("stage_delta", "10", MeasurementScope.SINCE_STAGE_START,
                CompletionMode.LATCHED);
        RequirementLeaf prestige = leaf("prestige_delta", "10", MeasurementScope.SINCE_PRESTIGE_START,
                CompletionMode.LATCHED);
        RequirementLeaf season = leaf("season_delta", "10", MeasurementScope.SINCE_SEASON_START,
                CompletionMode.LATCHED);
        List<RequirementDefinition> scoped = List.of(stage.definition(), prestige.definition(), season.definition());
        Map<RequirementId, MetricSample> initial = samples(Map.of(stage.id(), MetricValue.count(5),
                prestige.id(), MetricValue.count(5), season.id(), MetricValue.count(5)));
        BaselineInitializationService baselines = new BaselineInitializationService(states, CLOCK);
        for (MeasurementScope scope : List.of(MeasurementScope.SINCE_STAGE_START,
                MeasurementScope.SINCE_PRESTIGE_START, MeasurementScope.SINCE_SEASON_START)) {
            baselines.enterScope(PLAYER, scope, scopes.instance(scope).orElseThrow(), scoped, initial,
                    Map.of(PROVIDER, 1L));
        }
        assertEquals(3, states.baselineWrites);
        baselines.enterScope(PLAYER, MeasurementScope.SINCE_STAGE_START,
                scopes.instance(MeasurementScope.SINCE_STAGE_START).orElseThrow(), scoped, initial,
                Map.of(PROVIDER, 1L));
        assertEquals(4, states.baselineWrites);
        assertEquals(3, states.baselines.size(), "idempotent insert keeps one row per semantic scope key");

        Map<RequirementId, MetricSample> reached = samples(Map.of(live.id(), MetricValue.count(10),
                latched.id(), MetricValue.count(10), stage.id(), MetricValue.count(15),
                prestige.id(), MetricValue.count(15), season.id(), MetricValue.count(15)));
        RequirementEvaluationContext reachedContext = context(reached, scopes, states);
        var latchedPreview = evaluator().evaluate(latched, reachedContext);
        assertEquals(0, states.latches.size(), "read-only evaluation/simulation must not persist latches");
        new RequirementCompletionTransitionService(states, CLOCK).persistEligible(latchedPreview);
        assertEquals(1, states.latches.size());
        assertTrue(evaluator().evaluate(stage, reachedContext).satisfied());
        assertTrue(evaluator().evaluate(prestige, reachedContext).satisfied());
        assertTrue(evaluator().evaluate(season, reachedContext).satisfied());

        Map<RequirementId, MetricSample> dropped = samples(Map.of(live.id(), MetricValue.count(0),
                latched.id(), MetricValue.count(0)));
        assertEquals(RequirementEvaluationStatus.UNSATISFIED,
                evaluator().evaluate(live, context(dropped, scopes, states)).status());
        assertEquals(RequirementEvaluationStatus.SATISFIED,
                evaluator().evaluate(latched, context(dropped, scopes, states)).status());
        assertEquals(RequirementEvaluationStatus.UNSATISFIED,
                evaluator().evaluate(latched, context(dropped, scopes("cycle_b"), states)).status());

        RequirementLeaf changed = leaf("latched", "11", MeasurementScope.ABSOLUTE, CompletionMode.LATCHED);
        assertNotEquals(latched.definition().semanticFingerprint(), changed.definition().semanticFingerprint());
        assertEquals(RequirementEvaluationStatus.UNSATISFIED,
                evaluator().evaluate(changed, context(dropped, scopes, states)).status());
    }

    @Test
    @DisplayName("[A15-A17] A monotonic metric below a baseline returns reconciliation-needed unavailability")
    void failsSafelyWhenMonotonicMetricDrops() {
        InMemoryStates states = new InMemoryStates();
        RequirementLeaf leaf = leaf("reset_metric", "1", MeasurementScope.SINCE_STAGE_START, CompletionMode.LIVE);
        ScopeContext scopes = scopes("reset_scope");
        new BaselineInitializationService(states, CLOCK).enterScope(PLAYER, MeasurementScope.SINCE_STAGE_START,
                scopes.instance(MeasurementScope.SINCE_STAGE_START).orElseThrow(), List.of(leaf.definition()),
                samples(Map.of(leaf.id(), MetricValue.count(10))), Map.of(PROVIDER, 1L));
        var result = evaluator().evaluate(leaf,
                context(samples(Map.of(leaf.id(), MetricValue.count(5))), scopes, states));
        assertEquals(RequirementEvaluationStatus.UNAVAILABLE, result.status());
        assertTrue(result.explanation().summary().contains("fell below"));
    }

    @Test
    @DisplayName("[A14-A17] State readers returning a different baseline or latch key fail closed")
    void rejectsRequirementStateReturnedForAnotherKey() {
        RequirementLeaf scoped = leaf("wrong_baseline", "10", MeasurementScope.SINCE_STAGE_START,
                CompletionMode.LIVE);
        RequirementStateReader wrongBaseline = new RequirementStateReader() {
            @Override
            public Optional<RequirementBaseline> findBaseline(BaselineKey key) {
                BaselineKey wrong = new BaselineKey(key.playerId(), new RequirementId("foreign_baseline"),
                        key.scope(), key.scopeInstance(), key.semanticFingerprint());
                return Optional.of(new RequirementBaseline(wrong, MetricValue.count(0), 1, CLOCK.instant()));
            }

            @Override
            public Optional<RequirementLatch> findLatch(LatchKey key) {
                return Optional.empty();
            }
        };
        var baselineResult = evaluator().evaluate(scoped, context(
                samples(Map.of(scoped.id(), MetricValue.count(10))), scopes("wrong_key"), wrongBaseline));
        assertEquals(RequirementEvaluationStatus.ERROR, baselineResult.status());
        assertTrue(baselineResult.explanation().summary().contains("different key"));

        RequirementLeaf latched = leaf("wrong_latch", "10", MeasurementScope.ABSOLUTE,
                CompletionMode.LATCHED);
        RequirementStateReader wrongLatch = new RequirementStateReader() {
            @Override
            public Optional<RequirementBaseline> findBaseline(BaselineKey key) {
                return Optional.empty();
            }

            @Override
            public Optional<RequirementLatch> findLatch(LatchKey key) {
                LatchKey wrong = new LatchKey(key.playerId(), new RequirementId("foreign_latch"),
                        key.scope(), key.scopeInstance(), key.semanticFingerprint());
                return Optional.of(new RequirementLatch(wrong, CLOCK.instant()));
            }
        };
        var latchResult = evaluator().evaluate(latched, context(
                samples(Map.of(latched.id(), MetricValue.count(0))), scopes("wrong_key"), wrongLatch));
        assertEquals(RequirementEvaluationStatus.ERROR, latchResult.status());
        assertFalse(latchResult.satisfied());
        assertTrue(latchResult.explanation().summary().contains("different key"));
    }

    @Test
    @DisplayName("[A09-A12] Structural/type/scope validation rejects impossible and unsafe trees")
    void validatesTreeStructureAndCapabilities() {
        RequirementLeaf valid = leaf("valid", "1");
        var impossible = new RequirementGroup(new RequirementId("impossible"), RequirementGroupMode.ANY_X_OF_Y,
                List.of(RequirementChild.unweighted(valid)), ExactDecimal.parse("2"),
                CatchUpProfile.disabled(), "Impossible");
        var report = new RequirementTreeValidator().validate(impossible, Map.of(BINDING, DESCRIPTOR), 4);
        assertTrue(report.hasErrors());
        assertTrue(report.findings().stream().anyMatch(finding ->
                finding.code().equals("requirement.group.any_x.impossible")));

        RequirementDefinition booleanGreater = RequirementDefinition.create(new RequirementId("boolean_greater"),
                PROVIDER, new MetricId("flag"), MetricOperator.GREATER_THAN,
                RequirementTarget.single(MetricValue.bool(true)), MeasurementScope.ABSOLUTE,
                CompletionMode.LIVE, ScalingProfile.none(), CatchUpProfile.disabled(), Map.of(), Map.of(), false);
        MetricDescriptor flag = new MetricDescriptor(PROVIDER, new MetricId("flag"), MetricValueType.BOOLEAN,
                Set.of(MetricOperator.EQUAL), Set.of(MetricReadMode.CURRENT), false,
                MetricMonotonicity.NON_MONOTONIC, MetricResetPolicy.NOT_APPLICABLE, Map.of(),
                "Flag", "Flag", "", "test");
        assertTrue(new RequirementTreeValidator().validate(new RequirementLeaf(booleanGreater),
                Map.of(new MetricBinding(PROVIDER, new MetricId("flag")), flag), 4).hasErrors());

        RequirementNode deep = valid;
        for (int index = 0; index < 5; index++) {
            deep = RequirementGroup.all(new RequirementId("depth_" + index),
                    List.of(RequirementChild.unweighted(deep)));
        }
        assertTrue(new RequirementTreeValidator().validate(deep, Map.of(BINDING, DESCRIPTOR), 3).hasErrors());

        RequirementGroup empty = new RequirementGroup(new RequirementId("empty"), RequirementGroupMode.ALL,
                List.of(), ExactDecimal.parse("1"), CatchUpProfile.disabled(), "Empty");
        assertTrue(new RequirementTreeValidator().validate(empty, Map.of(BINDING, DESCRIPTOR), 3)
                .findings().stream().anyMatch(finding -> finding.code().equals("requirement.group.empty")));
    }

    private static RequirementEvaluator evaluator() {
        return new RequirementEvaluator(Map.of(BINDING, DESCRIPTOR));
    }

    private static RequirementChild child(RequirementLeaf leaf, String weight) {
        return new RequirementChild(leaf, ExactDecimal.parse(weight));
    }

    private static void assertGroup(
            RequirementGroupMode mode,
            String id,
            String threshold,
            List<RequirementChild> children,
            Map<RequirementId, MetricSample> samples,
            RequirementEvaluationStatus expected) {
        RequirementGroup group = new RequirementGroup(new RequirementId(id), mode, children,
                ExactDecimal.parse(threshold), CatchUpProfile.disabled(), id);
        RequirementEvaluationResult result = evaluator().evaluate(group, context(samples));
        assertEquals(expected, result.status(), id);
        assertEquals(children.size(), result.explanation().children().size(), id + " explanation children");
    }

    private static RequirementLeaf leaf(String id, String target) {
        return leaf(id, target, MeasurementScope.ABSOLUTE, CompletionMode.LIVE);
    }

    private static RequirementLeaf leaf(
            String id, String target, MeasurementScope scope, CompletionMode completion) {
        return new RequirementLeaf(RequirementDefinition.create(new RequirementId(id), PROVIDER, METRIC,
                MetricOperator.GREATER_OR_EQUAL, RequirementTarget.single(MetricValue.parse(
                        MetricValueType.COUNT, target)), scope, completion, ScalingProfile.none(),
                CatchUpProfile.disabled(), Map.of(), Map.of(), false));
    }

    private static Map<RequirementId, MetricSample> samples(Map<RequirementId, MetricValue> values) {
        LinkedHashMap<RequirementId, MetricSample> samples = new LinkedHashMap<>();
        values.forEach((id, value) -> samples.put(id, MetricSample.available(value, 1, CLOCK.instant(), "test")));
        return samples;
    }

    private static RequirementEvaluationContext context(Map<RequirementId, MetricSample> samples) {
        return context(samples, scopes("default_scope"), new InMemoryStates());
    }

    private static RequirementEvaluationContext context(
            Map<RequirementId, MetricSample> samples, ScopeContext scopes, RequirementStateReader states) {
        return new RequirementEvaluationContext(PLAYER, new ConfigRevisionId("revision"), Map.of(PROVIDER, 1L),
                0, ExactDecimal.ZERO, scopes, samples, states);
    }

    private static ScopeContext scopes(String suffix) {
        return new ScopeContext(Map.of(
                MeasurementScope.ABSOLUTE, new ScopeId("absolute_" + suffix),
                MeasurementScope.LIFETIME, new ScopeId("lifetime_" + suffix),
                MeasurementScope.SINCE_STAGE_START, new ScopeId("stage_" + suffix),
                MeasurementScope.SINCE_PRESTIGE_START, new ScopeId("prestige_" + suffix),
                MeasurementScope.SINCE_SEASON_START, new ScopeId("season_" + suffix)));
    }

    private static final class InMemoryStates implements RequirementStateWriter {
        private final Map<BaselineKey, RequirementBaseline> baselines = new LinkedHashMap<>();
        private final Map<LatchKey, RequirementLatch> latches = new LinkedHashMap<>();
        private int baselineWrites;

        @Override
        public Optional<RequirementBaseline> findBaseline(BaselineKey key) {
            return Optional.ofNullable(baselines.get(key));
        }

        @Override
        public Optional<RequirementLatch> findLatch(LatchKey key) {
            return Optional.ofNullable(latches.get(key));
        }

        @Override
        public RequirementBaseline initializeBaseline(RequirementBaseline baseline) {
            baselineWrites++;
            return baselines.computeIfAbsent(baseline.key(), ignored -> baseline);
        }

        @Override
        public RequirementLatch recordLatch(RequirementLatch latch) {
            return latches.computeIfAbsent(latch.key(), ignored -> latch);
        }
    }
}
