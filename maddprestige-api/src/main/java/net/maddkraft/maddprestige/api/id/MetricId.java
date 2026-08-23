package net.maddkraft.maddprestige.api.id;

import net.maddkraft.maddprestige.api.annotation.Stable;

/**
 * Immutable canonical metric identity.
 *
 * @param value canonical non-null metric value
 */
@Stable
public record MetricId(String value) implements StringIdentifier {
    public MetricId {
        value = IdentifierRules.requireValid(value, "metric ID");
    }
}
