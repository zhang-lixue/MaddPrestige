package net.maddkraft.maddprestige.api.id;

public record FieldId(String value) implements StringIdentifier {
    public FieldId {
        value = IdentifierRules.requireValid(value, "schema field ID");
    }
}
