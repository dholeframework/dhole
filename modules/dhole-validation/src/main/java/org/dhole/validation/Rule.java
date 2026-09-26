package org.dhole.validation;

/**
 * A reusable validation rule, used with {@code .rule(...)} on a field or {@code .check(...)} on the
 * whole object.
 *
 * @param <T> the validated value type
 */
@FunctionalInterface
public interface Rule<T> {

    ValidationResult validate(T value);
}
