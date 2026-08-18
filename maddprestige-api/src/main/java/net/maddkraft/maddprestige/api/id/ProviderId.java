package net.maddkraft.maddprestige.api.id;

import java.util.Objects;
import java.util.regex.Pattern;
import net.maddkraft.maddprestige.api.annotation.Stable;

/**
 * Stable provider identifier. External providers use {@code plugin_namespace:local_id}.
 *
 * @param value canonical local or owner-qualified identity, at most 128 characters
 */
@Stable
public record ProviderId(String value) implements StringIdentifier {
    private static final Pattern SAFE_PROVIDER_ID = Pattern.compile(
            "(?:[a-z0-9][a-z0-9._-]{0,63}|[a-z0-9][a-z0-9._-]{0,30}:[a-z0-9][a-z0-9._-]{0,30})");

    public ProviderId {
        value = Objects.requireNonNull(value, "provider ID");
        if (!SAFE_PROVIDER_ID.matcher(value).matches() || value.length() > IdentifierRules.MAX_LENGTH) {
            throw new IllegalArgumentException("provider ID must be a safe local or owner-qualified identifier: "
                    + value);
        }
    }
}
