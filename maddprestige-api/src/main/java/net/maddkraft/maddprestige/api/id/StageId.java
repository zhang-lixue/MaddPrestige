package net.maddkraft.maddprestige.api.id;

public record StageId(String value) implements StringIdentifier {
    public StageId {
        value = IdentifierRules.requireValid(value, "stage ID");
    }
}
