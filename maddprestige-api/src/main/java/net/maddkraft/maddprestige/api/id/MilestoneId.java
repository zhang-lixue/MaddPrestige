package net.maddkraft.maddprestige.api.id;

public record MilestoneId(String value) implements StringIdentifier {
    public MilestoneId {
        value = IdentifierRules.requireValid(value, "milestone ID");
    }
}
