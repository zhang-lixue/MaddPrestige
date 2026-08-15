package net.maddkraft.maddprestige.api.id;

public record CurrencyId(String value) implements StringIdentifier {
    public CurrencyId {
        value = IdentifierRules.requireValid(value, "currency ID");
    }
}
