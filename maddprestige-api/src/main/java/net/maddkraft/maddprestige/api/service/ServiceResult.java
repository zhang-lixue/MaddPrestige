package net.maddkraft.maddprestige.api.service;

import java.util.Objects;
import java.util.Optional;

/**
 * Canonical Stable service boundary result. Operational failures are values; futures fail exceptionally only for
 * caller/programmer contract violations such as null arguments.
 *
 * @param value immutable result value when successful
 * @param error bounded machine-readable operational failure when unsuccessful
 * @param <T> immutable Stable result type owned by the caller after completion
 */
public record ServiceResult<T>(Optional<T> value, Optional<ServiceError> error) {
    public ServiceResult {
        value = Objects.requireNonNull(value, "value");
        error = Objects.requireNonNull(error, "error");
        if (value.isPresent() == error.isPresent()) {
            throw new IllegalArgumentException("A service result must contain exactly one value or error");
        }
    }

    /**
     * Returns a successful immutable result and rejects null immediately.
     *
     * @param value non-null caller-owned value
     * @param <T> stable result type
     * @return immutable successful result
     */
    public static <T> ServiceResult<T> success(T value) {
        return new ServiceResult<>(Optional.of(Objects.requireNonNull(value, "value")), Optional.empty());
    }

    /**
     * Returns a failed result carrying one bounded structured error and rejects null immediately.
     *
     * @param error non-null bounded operational failure
     * @param <T> stable absent result type
     * @return immutable failed result
     */
    public static <T> ServiceResult<T> failure(ServiceError error) {
        return new ServiceResult<>(Optional.empty(), Optional.of(Objects.requireNonNull(error, "error")));
    }

    /**
     * Returns whether this instance contains a value; this method is non-blocking and side-effect free.
     *
     * @return true for success and false for a structured operational failure
     */
    public boolean successful() {
        return value.isPresent();
    }
}
