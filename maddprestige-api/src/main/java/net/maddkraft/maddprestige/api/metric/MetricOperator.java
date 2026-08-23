package net.maddkraft.maddprestige.api.metric;

import java.util.EnumSet;
import java.util.Set;
import net.maddkraft.maddprestige.api.annotation.Stable;

/** Operators supported by stable requirement comparisons. */
@Stable
public enum MetricOperator {
    /** Current value must be strictly greater than the target. */
    GREATER_THAN,

    /** Current value must be greater than or equal to the target. */
    GREATER_OR_EQUAL,

    /** Current value must be strictly less than the target. */
    LESS_THAN,

    /** Current value must be less than or equal to the target. */
    LESS_OR_EQUAL,

    /** Current value must equal the target under typed comparison. */
    EQUAL,

    /** Current value must not equal the target under typed comparison. */
    NOT_EQUAL,

    /** Current value must lie within an inclusive typed range. */
    IN_RANGE;

    private static final Set<MetricOperator> ORDERED = Set.copyOf(EnumSet.allOf(MetricOperator.class));
    private static final Set<MetricOperator> EQUALITY = Set.of(EQUAL, NOT_EQUAL);

    /**
     * Returns the immutable operator set compatible with a value type.
     *
     * @param type non-null value type
     * @return immutable shared operator set
     */
    public static Set<MetricOperator> compatibleWith(MetricValueType type) {
        return type.isNumeric() ? ORDERED : EQUALITY;
    }

    /**
     * Tests whether this operator supports a value type.
     *
     * @param type non-null value type
     * @return true when the type can be compared with this operator
     */
    public boolean supports(MetricValueType type) {
        return compatibleWith(type).contains(this);
    }
}
