package org.dhole.internal.validation;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.dhole.validation.Rule;

/**
 * One rule of a field, applied to a non-null value.
 */
sealed interface Check {

    Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");

    /**
     * A built-in rule: fails with {@code code} and {@code message} when the value does not pass.
     */
    record Builtin(String name, String code, String message, Predicate<Object> passes)
            implements Check {
    }

    record Custom(Rule<Object> rule) implements Check {
    }

    record Nested() implements Check {
    }

    record EachNested() implements Check {
    }

    static Check notBlank() {
        return new Builtin("notBlank", "NOT_BLANK", "Must not be blank.", value -> !text("notBlank", value).toString().isBlank());
    }

    static Check minLength(int length) {
        return new Builtin("minLength", "MIN_LENGTH", "Must contain at least " + length + " characters.",
                value -> text("minLength", value).length() >= length);
    }

    static Check maxLength(int length) {
        return new Builtin("maxLength", "MAX_LENGTH", "Must contain at most " + length + " characters.",
                value -> text("maxLength", value).length() <= length);
    }

    static Check email() {
        return new Builtin("email", "INVALID_EMAIL", "Must be a valid email address.",
                value -> EMAIL.matcher(text("email", value)).matches());
    }

    static Check min(long minimum) {
        return new Builtin("min", "MIN", "Must be at least " + minimum + ".",
                value -> number("min", value).compareTo(BigDecimal.valueOf(minimum)) >= 0);
    }

    static Check max(long maximum) {
        return new Builtin("max", "MAX", "Must be at most " + maximum + ".",
                value -> number("max", value).compareTo(BigDecimal.valueOf(maximum)) <= 0);
    }

    static Check positive() {
        return new Builtin("positive", "POSITIVE", "Must be positive.", value -> number("positive", value).signum() > 0);
    }

    static Check notEmpty() {
        return new Builtin("notEmpty", "NOT_EMPTY", "Must not be empty.", value -> {
            if (value instanceof CharSequence text) {
                return !text.isEmpty();
            }
            if (value instanceof Collection<?> collection) {
                return !collection.isEmpty();
            }
            if (value instanceof Map<?, ?> map) {
                return !map.isEmpty();
            }
            if (value.getClass().isArray()) {
                return Array.getLength(value) > 0;
            }
            throw notApplicable("notEmpty", value);
        });
    }

    @SuppressWarnings("unchecked")
    static Check custom(Rule<?> rule) {
        return new Custom((Rule<Object>) rule);
    }

    static Check nested() {
        return new Nested();
    }

    static Check eachNested() {
        return new EachNested();
    }

    private static CharSequence text(String rule, Object value) {
        if (value instanceof CharSequence text) {
            return text;
        }
        throw notApplicable(rule, value);
    }

    private static BigDecimal number(String rule, Object value) {
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        throw notApplicable(rule, value);
    }

    private static IllegalStateException notApplicable(String rule, Object value) {
        return new IllegalStateException("Validation rule " + rule + "() does not apply to "
                + value.getClass().getSimpleName() + " values");
    }
}
