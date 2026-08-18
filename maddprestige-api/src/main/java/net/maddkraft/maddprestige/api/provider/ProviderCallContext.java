package net.maddkraft.maddprestige.api.provider;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.annotation.Stable;
import net.maddkraft.maddprestige.api.id.ProviderId;

/**
 * Immutable identity and scheduling context for one bounded provider callback. The cancellation signal is owned by
 * MaddPrestige, remains valid only for the callback lifetime, and may transition asynchronously.
 *
 * @param correlationId non-null correlation identity; it is not a durable operation identity
 * @param deadline non-null UTC deadline after which optional work must stop
 * @param operationCode machine-readable callback operation code, 1-64 canonical characters
 * @param execution non-null execution/thread expectation; callbacks must not block a Paper server thread
 * @param ownerNamespace attested owner namespace, 1-31 canonical characters
 * @param providerId canonical provider identity when it is known; empty during metadata discovery
 * @param cancellation live read-only cancellation capability owned by MaddPrestige
 */
@Stable
public record ProviderCallContext(
        UUID correlationId,
        Instant deadline,
        String operationCode,
        ProviderExecutionExpectation execution,
        String ownerNamespace,
        Optional<ProviderId> providerId,
        ProviderCancellationSignal cancellation) {
    public ProviderCallContext {
        correlationId = Objects.requireNonNull(correlationId, "correlation ID");
        deadline = Objects.requireNonNull(deadline, "deadline");
        operationCode = Objects.requireNonNull(operationCode, "operation code");
        execution = Objects.requireNonNull(execution, "execution expectation");
        ownerNamespace = Objects.requireNonNull(ownerNamespace, "owner namespace");
        providerId = Objects.requireNonNull(providerId, "provider ID");
        cancellation = Objects.requireNonNull(cancellation, "cancellation signal");
        if (!operationCode.matches("[a-z0-9][a-z0-9._-]{0,63}")
                || !ownerNamespace.matches("[a-z0-9][a-z0-9_]{0,30}")) {
            throw new IllegalArgumentException("Provider call identity is outside stable bounds");
        }
        if (providerId.isPresent()
                && !providerId.orElseThrow().value().startsWith(ownerNamespace + ":")) {
            throw new IllegalArgumentException("Provider ID is outside the attested owner namespace");
        }
    }

    /**
     * Returns whether the deadline has elapsed at {@code now}. This method performs no I/O, does not mutate the
     * context, and rejects a null instant immediately.
     *
     * @param now current non-null instant supplied by the provider
     * @return true when {@code now} is equal to or after the callback deadline
     */
    public boolean expired(Instant now) {
        return !Objects.requireNonNull(now, "current time").isBefore(deadline);
    }

    /**
     * Returns the current owner-controlled cancellation state without blocking. Providers must treat true as a
     * request to stop optional work and complete promptly.
     *
     * @return true after shutdown, timeout, lifecycle invalidation, or explicit callback cancellation
     */
    public boolean cancellationRequested() {
        return cancellation.cancellationRequested();
    }
}
