package net.maddkraft.maddprestige.api.id;

public record ScopeId(String value) implements StringIdentifier {
    public ScopeId {
        value = IdentifierRules.requireValid(value, "scope ID");
    }
}
