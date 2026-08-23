package net.maddkraft.maddprestige.api.provider;

import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.annotation.Stable;

/** Opaque, owner-scoped provider lifecycle capability associated by the Paper bridge. */
@Stable
public interface ProviderRegistrationHandle {
    /**
     * Returns the canonical owner-attested identifier.
     *
     * @return non-null immutable identity in {@code plugin_namespace:local_id} form
     */
    ProviderId providerId();

    /**
     * Unregisters this exact generation asynchronously; stale calls fail closed and cannot affect a replacement.
     * Repeated calls are safe, and operational invalidation completes normally rather than leaking registry errors.
     *
     * @return non-null completion stage for this handle's invalidation attempt
     */
    CompletionStage<Void> unregister();
}
