package org.dhole.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.dhole.http.HttpStatus;
import org.dhole.validation.ValidationError;
import org.dhole.validation.ValidationResult;
import org.junit.jupiter.api.Test;

class ErrorResponseTest {

    @Test
    void emptyDetailsFieldsAndRequestIdAreOmitted() {
        assertEquals(Map.of("error", Map.of("code", "NOT_FOUND", "message", "User not found.")),
                ErrorResponse.of("NOT_FOUND", "User not found.").envelope());
        assertEquals(Map.of("error", Map.of("code", "CONFLICT", "message", "Taken.", "details", Map.of("field", "email"),
                "requestId", "id")), ErrorResponse.of("CONFLICT", "Taken.").details(Map.of("field", "email"))
                        .requestId("id").envelope());
    }

    @Test
    void validationGroupsErrorsByFieldPathInOrder() {
        ErrorResponse response = ErrorResponse.validation(ValidationResult.of(List.of(
                new ValidationError("items[1].quantity", "POSITIVE", "Must be positive."),
                new ValidationError("customer.name", "REQUIRED", "Is required."),
                new ValidationError("items[1].quantity", "MAX", "Must be at most 9."),
                new ValidationError("", "MISMATCH", "Mismatch."))));

        assertEquals("VALIDATION_ERROR", response.code());
        assertEquals(List.of("items[1].quantity", "customer.name", ""), List.copyOf(response.fields().keySet()));
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.envelope().get("error");
        assertEquals(List.of(Map.of("code", "POSITIVE", "message", "Must be positive."),
                Map.of("code", "MAX", "message", "Must be at most 9.")),
                ((Map<?, ?>) error.get("fields")).get("items[1].quantity"));
        assertThrows(IllegalArgumentException.class, () -> ErrorResponse.validation(ValidationResult.valid()));
    }

    @Test
    void appExceptionCodesAreStableIdentifiers() {
        AppException error = Errors.notFound("User");

        assertEquals("NOT_FOUND", error.code());
        assertEquals(HttpStatus.NOT_FOUND, error.status());
        assertEquals("User not found.", error.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new AppException("not-a-code", HttpStatus.CONFLICT, "x"));
    }
}
