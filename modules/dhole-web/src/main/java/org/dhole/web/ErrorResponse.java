package org.dhole.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.dhole.validation.ValidationError;
import org.dhole.validation.ValidationResult;

/**
 * The error envelope (ERRORS.md §7, VALIDATION.md §12):
 *
 * <pre>{@code
 * {"error": {"code": "...", "message": "...", "details": {...}, "fields": {...}, "requestId": "..."}}
 * }</pre>
 *
 * {@code details} and {@code fields} are omitted when empty. The request pipeline sets the request
 * ID. Immutable.
 */
public final class ErrorResponse {

    private final String code;
    private final String message;
    private final Map<String, Object> details;
    private final Map<String, List<ValidationError>> fields;
    private final String requestId;

    private ErrorResponse(String code, String message, Map<String, Object> details,
            Map<String, List<ValidationError>> fields, String requestId) {
        this.code = Objects.requireNonNull(code, "code");
        this.message = Objects.requireNonNull(message, "message");
        this.details = details;
        this.fields = fields;
        this.requestId = requestId;
    }

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, Map.of(), Map.of(), null);
    }

    /**
     * {@code VALIDATION_ERROR} with the failures grouped by field path, in their order.
     *
     * @throws IllegalArgumentException if the result is valid
     */
    public static ErrorResponse validation(ValidationResult result) {
        if (result.isValid()) {
            throw new IllegalArgumentException("A valid result has no errors");
        }
        Map<String, List<ValidationError>> fields = new LinkedHashMap<>();
        for (ValidationError error : result.errors()) {
            fields.computeIfAbsent(error.field(), field -> new ArrayList<>()).add(error);
        }
        fields.replaceAll((field, errors) -> List.copyOf(errors));
        return new ErrorResponse("VALIDATION_ERROR", "The request contains invalid fields.", Map.of(),
                Collections.unmodifiableMap(fields), null);
    }

    /**
     * Returns a copy with details; values must be safe to show to the client.
     */
    public ErrorResponse details(Map<String, ?> details) {
        return new ErrorResponse(code, message, Collections.unmodifiableMap(new LinkedHashMap<>(details)), fields,
                requestId);
    }

    /**
     * Returns a copy with the request ID; set by the request pipeline.
     */
    public ErrorResponse requestId(String requestId) {
        return new ErrorResponse(code, message, details, fields, Objects.requireNonNull(requestId, "requestId"));
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    public Map<String, Object> details() {
        return details;
    }

    public Map<String, List<ValidationError>> fields() {
        return fields;
    }

    public Optional<String> requestId() {
        return Optional.ofNullable(requestId);
    }

    /**
     * Returns the envelope as plain maps and lists, ready for any serializer.
     */
    public Map<String, Object> envelope() {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        if (!details.isEmpty()) {
            error.put("details", details);
        }
        if (!fields.isEmpty()) {
            Map<String, Object> byField = new LinkedHashMap<>();
            fields.forEach((field, errors) -> byField.put(field, errors.stream().map(failure -> {
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("code", failure.code());
                entry.put("message", failure.message());
                return entry;
            }).toList()));
            error.put("fields", byField);
        }
        if (requestId != null) {
            error.put("requestId", requestId);
        }
        return Map.of("error", error);
    }

    @Override
    public String toString() {
        return "ErrorResponse" + envelope();
    }
}
