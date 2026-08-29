package net.maddkraft.maddprestige.api.id;

import net.maddkraft.maddprestige.api.annotation.Stable;

/**
 * Immutable stage-era identity retained for stable source and binary compatibility. Active numeric Prestige does not
 * use stage identities for progression authority.
 *
 * @param value canonical non-null stage value
 */
@Stable
public record StageId(String value) implements StringIdentifier {
    public StageId {
        value = IdentifierRules.requireValid(value, "stage ID");
    }
}
