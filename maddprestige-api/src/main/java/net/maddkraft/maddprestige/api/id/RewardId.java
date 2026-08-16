package net.maddkraft.maddprestige.api.id;

public record RewardId(String value) implements StringIdentifier {
    public RewardId {
        value = IdentifierRules.requireValid(value, "reward ID");
    }

    @Override
    public String toString() {
        return value;
    }
}
