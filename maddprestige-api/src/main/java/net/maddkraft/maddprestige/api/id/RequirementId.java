package net.maddkraft.maddprestige.api.id;

public record RequirementId(String value) implements StringIdentifier {
    public RequirementId {
        value = IdentifierRules.requireValid(value, "requirement ID");
    }

    @Override
    public String toString() {
        return value;
    }
}
