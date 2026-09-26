package org.dhole.http;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Immutable HTTP headers. Names are case-insensitive and reported in lower case; a name may have
 * several values, kept in order.
 */
public final class Headers {

    private static final Headers EMPTY = new Headers(Map.of());

    private final Map<String, List<String>> values;

    private Headers(Map<String, List<String>> values) {
        this.values = values;
    }

    public static Headers empty() {
        return EMPTY;
    }

    public static Headers of(Map<String, List<String>> headers) {
        Objects.requireNonNull(headers, "headers");
        Map<String, List<String>> normalized = new TreeMap<>();
        headers.forEach((name, list) -> normalized
                .computeIfAbsent(normalize(name), key -> new ArrayList<>())
                .addAll(List.copyOf(list)));
        normalized.replaceAll((name, list) -> List.copyOf(list));
        return new Headers(Collections.unmodifiableMap(normalized));
    }

    /**
     * Returns the first value of a header, or {@code null} when absent.
     */
    public String first(String name) {
        List<String> list = values.get(normalize(name));
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    /**
     * Returns every value of a header; empty when absent.
     */
    public List<String> all(String name) {
        return values.getOrDefault(normalize(name), List.of());
    }

    /**
     * Returns the header names, in lower case and sorted.
     */
    public Set<String> names() {
        return values.keySet();
    }

    /**
     * Returns a copy with one more value for {@code name}.
     */
    public Headers with(String name, String value) {
        Objects.requireNonNull(value, "value");
        Map<String, List<String>> copy = new TreeMap<>(values);
        List<String> list = new ArrayList<>(copy.getOrDefault(normalize(name), List.of()));
        list.add(value);
        copy.put(normalize(name), List.copyOf(list));
        return new Headers(Collections.unmodifiableMap(copy));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Headers headers && values.equals(headers.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return "Headers" + values.keySet();
    }

    private static String normalize(String name) {
        Objects.requireNonNull(name, "name");
        return name.toLowerCase(Locale.ROOT);
    }
}
