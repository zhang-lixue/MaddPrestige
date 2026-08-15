package net.maddkraft.maddprestige.api.id;

public record ProviderId(String value) implements StringIdentifier {
    public ProviderId {
        value = IdentifierRules.requireValid(value, "provider ID");
    }
}
