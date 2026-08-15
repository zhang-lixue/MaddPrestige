package net.maddkraft.maddprestige.api.id;

public record MetricId(String value) implements StringIdentifier {
    public MetricId {
        value = IdentifierRules.requireValid(value, "metric ID");
    }
}
