package net.maddkraft.maddprestige.api.id;

public record EntitlementId(String value) implements StringIdentifier {
    public EntitlementId {
        value = IdentifierRules.requireValid(value, "entitlement ID");
    }
}
