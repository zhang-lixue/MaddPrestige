package net.maddkraft.maddprestige.api.id;

public record SeasonId(String value) implements StringIdentifier {
    public SeasonId {
        value = IdentifierRules.requireValid(value, "season ID");
    }
}
