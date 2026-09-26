package org.dhole.validation;

/**
 * Validates {@link Validatable} values with their declared rules, inside or outside HTTP.
 */
public interface Validator {

    /**
     * @throws IllegalArgumentException if the value is not {@link Validatable}
     */
    ValidationResult validate(Object value);
}
