package org.dhole.validation;

import java.util.Objects;

/**
 * One validation failure.
 *
 * @param field the path of the invalid value, for example {@code email}, {@code customer.name} or
 *        {@code items[0].quantity}; empty for an object-level failure
 * @param code the stable, machine-readable code, for example {@code INVALID_EMAIL}
 * @param message a human-readable message that never contains the invalid value
 */
public record ValidationError(String field, String code, String message) {

    public ValidationError {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }
}
