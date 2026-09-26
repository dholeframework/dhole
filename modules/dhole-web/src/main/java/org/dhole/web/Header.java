package org.dhole.web;

import java.util.Objects;
import java.util.Optional;

/**
 * Explicitly binds a header value: {@code Response locale(Header<String> acceptLanguage)}. The
 * header name is the handler parameter name in kebab-case: {@code acceptLanguage} reads
 * {@code Accept-Language}.
 *
 * @param <T> the converted type
 */
public final class Header<T> {

    private final String name;
    private final T value;

    private Header(String name, T value) {
        this.name = Objects.requireNonNull(name, "name");
        this.value = value;
    }

    /**
     * @param value the converted value, or {@code null} when the request has none
     */
    public static <T> Header<T> of(String name, T value) {
        return new Header<>(name, value);
    }

    /**
     * Returns the value.
     *
     * @throws BindingException if the request has no such header value (answered with 400)
     */
    public T value() {
        if (value == null) {
            throw new BindingException("Missing header parameter '" + name + "'");
        }
        return value;
    }

    public Optional<T> optional() {
        return Optional.ofNullable(value);
    }

    @Override
    public String toString() {
        return "Header[" + name + "]";
    }
}
