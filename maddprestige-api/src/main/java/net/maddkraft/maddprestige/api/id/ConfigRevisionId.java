package net.maddkraft.maddprestige.api.id;

import net.maddkraft.maddprestige.api.annotation.Stable;

/**
 * Exact immutable identity of one canonical configuration revision.
 *
 * @param value canonical non-null revision value
 */
@Stable
public record ConfigRevisionId(String value) implements StringIdentifier {
    public ConfigRevisionId {
        value = IdentifierRules.requireValid(value, "configuration revision ID");
    }
}
