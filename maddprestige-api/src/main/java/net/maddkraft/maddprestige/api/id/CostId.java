package net.maddkraft.maddprestige.api.id;

public record CostId(String value) implements StringIdentifier {
    public CostId {
        value = IdentifierRules.requireValid(value, "cost ID");
    }

    @Override
    public String toString() {
        return value;
    }
}
