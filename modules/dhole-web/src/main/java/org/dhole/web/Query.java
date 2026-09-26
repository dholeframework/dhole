package org.dhole.web;

import java.util.Objects;
import java.util.Optional;

/**
 * Explicitly binds a query value: {@code List<User> search(Query<String> term)}. The query
 * parameter has the name of the handler parameter.
 *
 * @param <T> the converted type
 */
public final class Query<T> {

    private final String name;
    private final T value;

    private Query(String name, T value) {
        this.name = Objects.requireNonNull(name, "name");
        this.value = value;
    }

    /**
     * @param value the converted value, or {@code null} when the request has none
     */
    public static <T> Query<T> of(String name, T value) {
        return new Query<>(name, value);
    }

    /**
     * Returns the value.
     *
     * @throws BindingException if the request has no such query value (answered with 400)
     */
    public T value() {
        if (value == null) {
            throw new BindingException("Missing query parameter '" + name + "'");
        }
        return value;
    }

    public Optional<T> optional() {
        return Optional.ofNullable(value);
    }

    @Override
    public String toString() {
        return "Query[" + name + "]";
    }
}
