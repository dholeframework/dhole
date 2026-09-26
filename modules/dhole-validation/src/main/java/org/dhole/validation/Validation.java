package org.dhole.validation;

import java.util.List;

/**
 * Results for custom rules:
 *
 * <pre>{@code
 * Rule<String> strongPassword = value -> value.length() >= 12
 *     ? Validation.success()
 *     : Validation.failure("PASSWORD_TOO_WEAK", "Password must contain at least 12 characters.");
 * }</pre>
 */
public final class Validation {

    private Validation() {
    }

    public static ValidationResult success() {
        return ValidationResult.valid();
    }

    /**
     * A failure of the value the rule was applied to; the framework adds the field path.
     */
    public static ValidationResult failure(String code, String message) {
        return ValidationResult.of(List.of(new ValidationError("", code, message)));
    }
}
