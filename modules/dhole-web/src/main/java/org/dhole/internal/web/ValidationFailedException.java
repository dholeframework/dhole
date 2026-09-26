package org.dhole.internal.web;

import java.util.Objects;

import org.dhole.validation.ValidationResult;

/**
 * A bound request body failed its declared rules; answered with {@code 422 VALIDATION_ERROR}.
 */
final class ValidationFailedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient ValidationResult result;

    ValidationFailedException(ValidationResult result) {
        super("The request contains invalid fields.", null, false, false);
        this.result = Objects.requireNonNull(result, "result");
    }

    ValidationResult result() {
        return result;
    }
}
