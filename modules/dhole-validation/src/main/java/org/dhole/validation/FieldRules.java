package org.dhole.validation;

/**
 * The rules of one field. A {@code null} value fails only {@link #required()}; without it, {@code null}
 * is accepted and the other rules of the field do not run.
 *
 * @param <T> the validated type
 * @param <V> the field type
 */
public interface FieldRules<T, V> extends Rules<T> {

    /** Must not be {@code null}: {@code REQUIRED}. */
    FieldRules<T, V> required();

    /** Text must contain a non-whitespace character: {@code NOT_BLANK}. */
    FieldRules<T, V> notBlank();

    /** Text must have at least {@code length} characters: {@code MIN_LENGTH}. */
    FieldRules<T, V> minLength(int length);

    /** Text must have at most {@code length} characters: {@code MAX_LENGTH}. */
    FieldRules<T, V> maxLength(int length);

    /** Text must be an email address: {@code INVALID_EMAIL}. */
    FieldRules<T, V> email();

    /** Number must be at least {@code minimum}: {@code MIN}. */
    FieldRules<T, V> min(long minimum);

    /** Number must be at most {@code maximum}: {@code MAX}. */
    FieldRules<T, V> max(long maximum);

    /** Number must be greater than zero: {@code POSITIVE}. */
    FieldRules<T, V> positive();

    /** Text, collection, map or array must not be empty: {@code NOT_EMPTY}. */
    FieldRules<T, V> notEmpty();

    /** A custom rule; its failures get this field's path. */
    FieldRules<T, V> rule(Rule<? super V> rule);

    /** Validates the value with its own type's rules; paths become {@code field.nested}. */
    FieldRules<T, V> nested();

    /** Validates every element with its type's rules; paths become {@code field[index].nested}. */
    FieldRules<T, V> eachNested();
}
