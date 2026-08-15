package net.maddkraft.maddprestige.api.result;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class Result<T> {
    private final T value;
    private final List<StructuredError> errors;

    private Result(T value, List<StructuredError> errors) {
        this.value = value;
        this.errors = List.copyOf(errors);
    }

    public static <T> Result<T> success(T value) {
        return new Result<>(Objects.requireNonNull(value, "value"), List.of());
    }

    public static <T> Result<T> failure(StructuredError error) {
        return new Result<>(null, List.of(Objects.requireNonNull(error, "error")));
    }

    public boolean isSuccess() {
        return errors.isEmpty();
    }

    public Optional<T> value() {
        return Optional.ofNullable(value);
    }

    public List<StructuredError> errors() {
        return errors;
    }
}
