package org.dhole.validation;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of a validation: valid, or the failures in deterministic order (fields in declaration
 * order, rules of a field in declaration order, object-level checks last).
 */
public final class ValidationResult {

    private static final ValidationResult VALID = new ValidationResult(List.of());

    private final List<ValidationError> errors;

    private ValidationResult(List<ValidationError> errors) {
        this.errors = List.copyOf(errors);
    }

    public static ValidationResult valid() {
        return VALID;
    }

    public static ValidationResult of(List<ValidationError> errors) {
        Objects.requireNonNull(errors, "errors");
        return errors.isEmpty() ? VALID : new ValidationResult(errors);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public List<ValidationError> errors() {
        return errors;
    }

    /**
     * Returns the errors of one field path.
     */
    public List<ValidationError> errors(String field) {
        return errors.stream().filter(error -> error.field().equals(field)).toList();
    }

    @Override
    public String toString() {
        return isValid() ? "ValidationResult[valid]" : "ValidationResult" + errors;
    }
}
