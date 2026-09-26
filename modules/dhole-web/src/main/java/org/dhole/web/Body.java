package org.dhole.web;

import java.util.Objects;

/**
 * Explicitly binds the request body: {@code User create(Body<CreateUser> input)}. Usually
 * unnecessary on POST, PUT and PATCH, where a single structured parameter is the body anyway; needed
 * to read a body on GET or DELETE.
 *
 * @param <T> the deserialized type
 */
public final class Body<T> {

    private final T value;

    private Body(T value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public static <T> Body<T> of(T value) {
        return new Body<>(value);
    }

    public T value() {
        return value;
    }

    @Override
    public String toString() {
        return "Body[" + value.getClass().getSimpleName() + "]";
    }
}
