package net.maddkraft.maddprestige.core.entitlement;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class EntitlementMergeEngine {
    private static final Comparator<EntitlementContribution> SOURCE_ORDER =
            Comparator.comparing(EntitlementContribution::sourceId);

    public Optional<EffectiveEntitlement> merge(
            EntitlementDefinition definition,
            List<EntitlementContribution> contributions) {
        Objects.requireNonNull(definition, "definition");
        List<EntitlementContribution> ordered = contributions.stream().sorted(SOURCE_ORDER).toList();
        HashSet<String> sources = new HashSet<>();
        for (EntitlementContribution contribution : ordered) {
            if (contribution.value().type() != definition.type()) {
                throw new IllegalArgumentException("Entitlement contribution type does not match definition");
            }
            if (!sources.add(contribution.sourceId())) {
                throw new IllegalArgumentException("Duplicate entitlement source: " + contribution.sourceId());
            }
        }
        if (ordered.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(switch (definition.strategy()) {
            case MAX -> extrema(definition, ordered, true);
            case MIN -> extrema(definition, ordered, false);
            case SUM -> sum(definition, ordered);
            case OVERRIDE -> override(definition, ordered);
            case BOOLEAN_OR -> booleanOr(definition, ordered);
        });
    }

    private static EffectiveEntitlement extrema(
            EntitlementDefinition definition,
            List<EntitlementContribution> ordered,
            boolean maximum) {
        Comparator<EntitlementContribution> comparator = Comparator.comparing(value -> value.value().number());
        BigDecimal selectedNumber = (maximum ? ordered.stream().max(comparator) : ordered.stream().min(comparator))
                .orElseThrow().value().number();
        List<String> sources = ordered.stream().filter(value -> value.value().number().compareTo(selectedNumber) == 0)
                .map(EntitlementContribution::sourceId).toList();
        return new EffectiveEntitlement(definition.id(), EntitlementValue.numeric(definition.type(), selectedNumber),
                ordered, sources, definition.strategy() + " selected " + String.join(",", sources));
    }

    private static EffectiveEntitlement sum(
            EntitlementDefinition definition,
            List<EntitlementContribution> ordered) {
        BigDecimal total = BigDecimal.ZERO;
        for (EntitlementContribution contribution : ordered) {
            total = total.add(contribution.value().number());
            if (total.precision() > 38 || Math.max(total.scale(), 0) > 18) {
                throw new ArithmeticException("Entitlement SUM exceeds precision/scale bounds");
            }
        }
        EntitlementValue value = EntitlementValue.numeric(definition.type(), total);
        List<String> sources = ordered.stream().map(EntitlementContribution::sourceId).toList();
        return new EffectiveEntitlement(definition.id(), value, ordered, sources,
                "SUM combined " + String.join(",", sources));
    }

    private static EffectiveEntitlement override(
            EntitlementDefinition definition,
            List<EntitlementContribution> ordered) {
        EntitlementContribution selected = ordered.stream()
                .max(Comparator.comparingInt(EntitlementContribution::priority)
                        .thenComparing(EntitlementContribution::sourceId, Comparator.reverseOrder()))
                .orElseThrow();
        return new EffectiveEntitlement(definition.id(), selected.value(), ordered, List.of(selected.sourceId()),
                "OVERRIDE selected priority " + selected.priority() + " source " + selected.sourceId());
    }

    private static EffectiveEntitlement booleanOr(
            EntitlementDefinition definition,
            List<EntitlementContribution> ordered) {
        boolean value = ordered.stream().anyMatch(source -> source.value().booleanValue());
        List<String> sources = ordered.stream().filter(source -> source.value().booleanValue() == value)
                .map(EntitlementContribution::sourceId).toList();
        return new EffectiveEntitlement(definition.id(), EntitlementValue.bool(value), ordered, sources,
                "BOOLEAN_OR evaluated " + value);
    }
}
