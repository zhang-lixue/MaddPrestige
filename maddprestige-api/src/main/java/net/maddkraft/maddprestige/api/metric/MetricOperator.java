package net.maddkraft.maddprestige.api.metric;

import java.util.EnumSet;
import java.util.Set;

public enum MetricOperator {
    GREATER_THAN,
    GREATER_OR_EQUAL,
    LESS_THAN,
    LESS_OR_EQUAL,
    EQUAL,
    NOT_EQUAL,
    IN_RANGE;

    private static final Set<MetricOperator> ORDERED = Set.copyOf(EnumSet.allOf(MetricOperator.class));
    private static final Set<MetricOperator> EQUALITY = Set.of(EQUAL, NOT_EQUAL);

    public static Set<MetricOperator> compatibleWith(MetricValueType type) {
        return type.isNumeric() ? ORDERED : EQUALITY;
    }

    public boolean supports(MetricValueType type) {
        return compatibleWith(type).contains(this);
    }
}
