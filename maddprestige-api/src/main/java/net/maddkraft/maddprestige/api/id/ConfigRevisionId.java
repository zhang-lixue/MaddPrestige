package net.maddkraft.maddprestige.api.id;

public record ConfigRevisionId(String value) implements StringIdentifier {
    public ConfigRevisionId {
        value = IdentifierRules.requireValid(value, "configuration revision ID");
    }
}
