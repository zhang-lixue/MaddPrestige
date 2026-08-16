package net.maddkraft.maddprestige.core.entitlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import net.maddkraft.maddprestige.api.id.EntitlementId;
import org.junit.jupiter.api.Test;

class EntitlementMergeEngineTest {
    private final EntitlementMergeEngine engine = new EntitlementMergeEngine();

    @Test
    void maxDoesNotAccidentallyStackSourcesAndHasDeterministicProvenance() {
        EntitlementDefinition definition = new EntitlementDefinition(new EntitlementId("generic_limit"),
                EntitlementValueType.INTEGER, EntitlementMergeStrategy.MAX);
        var result = engine.merge(definition, List.of(
                new EntitlementContribution("prestige", 0, EntitlementValue.integer(8)),
                new EntitlementContribution("base", 0, EntitlementValue.integer(1)),
                new EntitlementContribution("supporter", 0, EntitlementValue.integer(15)))).orElseThrow();

        assertEquals(EntitlementValue.integer(15), result.value());
        assertEquals(List.of("supporter"), result.effectiveSources());
        assertEquals(List.of("base", "prestige", "supporter"), result.orderedContributions().stream()
                .map(EntitlementContribution::sourceId).toList());
    }

    @Test
    void sumIsExactDeterministicAndOverflowSafe() {
        EntitlementDefinition definition = new EntitlementDefinition(new EntitlementId("generic_capacity"),
                EntitlementValueType.INTEGER, EntitlementMergeStrategy.SUM);
        var result = engine.merge(definition, List.of(
                new EntitlementContribution("z", 0, EntitlementValue.integer(8)),
                new EntitlementContribution("a", 0, EntitlementValue.integer(15)),
                new EntitlementContribution("m", 0, EntitlementValue.integer(1)))).orElseThrow();

        assertEquals(EntitlementValue.integer(24), result.value());
        assertEquals(List.of("a", "m", "z"), result.effectiveSources());
        assertThrows(ArithmeticException.class, () -> engine.merge(definition, List.of(
                new EntitlementContribution("a", 0, EntitlementValue.integer(Long.MAX_VALUE)),
                new EntitlementContribution("b", 0, EntitlementValue.integer(1)))));
    }

    @Test
    void rejectsDuplicateSourcesAndInvalidTypeStrategy() {
        EntitlementDefinition definition = new EntitlementDefinition(new EntitlementId("generic_capacity"),
                EntitlementValueType.EXACT_DECIMAL, EntitlementMergeStrategy.SUM);
        assertThrows(IllegalArgumentException.class, () -> engine.merge(definition, List.of(
                new EntitlementContribution("same", 0, EntitlementValue.decimal("1.2")),
                new EntitlementContribution("same", 1, EntitlementValue.decimal("2.3")))));
        assertThrows(IllegalArgumentException.class, () -> new EntitlementDefinition(
                new EntitlementId("invalid"), EntitlementValueType.INTEGER, EntitlementMergeStrategy.BOOLEAN_OR));
    }
}
