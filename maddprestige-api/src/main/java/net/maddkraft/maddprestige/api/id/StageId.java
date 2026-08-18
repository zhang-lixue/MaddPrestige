package net.maddkraft.maddprestige.api.id;

import net.maddkraft.maddprestige.api.annotation.Stable;

/**
 * Immutable canonical progression-stage identity.
 *
 * @param value canonical non-null stage value
 */
@Stable
public record StageId(String value) implements StringIdentifier {
    public StageId {
        value = IdentifierRules.requireValid(value, "stage ID");
    }
}
