package net.maddkraft.maddprestige.api.id;

import net.maddkraft.maddprestige.api.annotation.Stable;

/**
 * Deliberate extension point for immutable, canonical, string-backed identifiers.
 *
 * <p>Implementations must reject {@code null}, blank, control-character, or non-canonical values at construction,
 * provide value-based equality and hashing, and return the same non-null value for their lifetime. Implementations
 * are thread-safe value objects; callers may retain them indefinitely. Stable MaddPrestige APIs accept documented
 * identifier records rather than relying on arbitrary third-party implementations.</p>
 */
@Stable
public interface StringIdentifier {
    /**
     * Returns the immutable canonical wire value.
     *
     * @return non-null, non-blank canonical value, stable for this object's lifetime
     */
    String value();
}
