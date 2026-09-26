package org.dhole.web;

import java.util.Objects;

/**
 * Explicitly binds a path parameter with the same name as the handler parameter:
 * {@code User find(Path<Long> id)} on {@code /users/{id}}. Usually unnecessary: a parameter named
 * like a path placeholder is bound from the path anyway.
 *
 * @param <T> the converted type
 */
public final class Path<T> {

    private final T value;

    private Path(T value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public static <T> Path<T> of(T value) {
        return new Path<>(value);
    }

    public T value() {
        return value;
    }

    @Override
    public String toString() {
        return "Path[" + value + "]";
    }
}
