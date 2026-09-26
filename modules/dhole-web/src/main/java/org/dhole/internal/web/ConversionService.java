package org.dhole.internal.web;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Converts raw path, query and header strings to the built-in scalar types of
 * PARAMETER_BINDING.md §4 and §38: {@code String}, {@code byte}, {@code short}, {@code int},
 * {@code long}, {@code float}, {@code double}, {@code boolean} and their wrappers, {@code UUID},
 * enums (exact constant name), {@code LocalDate}, {@code LocalDateTime} and {@code Instant} (ISO-8601).
 *
 * <p>Strict: no surrounding whitespace, booleans only {@code true}/{@code false}, no non-finite
 * decimals. Failures are {@link IllegalArgumentException}s, reported by the binder as 400.
 */
final class ConversionService {

    private static final Map<Class<?>, Function<String, Object>> CONVERTERS = Map.ofEntries(
            Map.entry(String.class, value -> value),
            Map.entry(byte.class, Byte::parseByte),
            Map.entry(Byte.class, Byte::parseByte),
            Map.entry(short.class, Short::parseShort),
            Map.entry(Short.class, Short::parseShort),
            Map.entry(int.class, Integer::parseInt),
            Map.entry(Integer.class, Integer::parseInt),
            Map.entry(long.class, Long::parseLong),
            Map.entry(Long.class, Long::parseLong),
            Map.entry(float.class, value -> finite(Float.parseFloat(value))),
            Map.entry(Float.class, value -> finite(Float.parseFloat(value))),
            Map.entry(double.class, value -> finite(Double.parseDouble(value))),
            Map.entry(Double.class, value -> finite(Double.parseDouble(value))),
            Map.entry(boolean.class, ConversionService::parseBoolean),
            Map.entry(Boolean.class, ConversionService::parseBoolean),
            Map.entry(UUID.class, UUID::fromString),
            Map.entry(LocalDate.class, LocalDate::parse),
            Map.entry(LocalDateTime.class, LocalDateTime::parse),
            Map.entry(Instant.class, Instant::parse));

    private ConversionService() {
    }

    static boolean supports(Class<?> type) {
        return CONVERTERS.containsKey(type) || type.isEnum();
    }

    /**
     * @throws IllegalArgumentException if the value cannot be converted
     */
    static Object convert(String value, Class<?> type) {
        if (!value.equals(value.strip())) {
            throw new IllegalArgumentException("surrounding whitespace");
        }
        if (type.isEnum()) {
            for (Object constant : type.getEnumConstants()) {
                if (((Enum<?>) constant).name().equals(value)) {
                    return constant;
                }
            }
            throw new IllegalArgumentException("unknown constant");
        }
        Function<String, Object> converter = CONVERTERS.get(type);
        if (converter == null) {
            throw new IllegalArgumentException("unsupported type " + type.getName());
        }
        try {
            return converter.apply(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("invalid date or time", e);
        }
    }

    /**
     * Returns a readable name of the expected type for error messages, for example {@code long}.
     */
    static String describe(Class<?> type) {
        return type.getSimpleName();
    }

    private static Object parseBoolean(String value) {
        return switch (value) {
            case "true" -> Boolean.TRUE;
            case "false" -> Boolean.FALSE;
            default -> throw new IllegalArgumentException("not a boolean");
        };
    }

    private static Object finite(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("not a finite number");
        }
        return value;
    }

    private static Object finite(float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("not a finite number");
        }
        return value;
    }
}
