package org.dhole.web;

import java.util.Objects;
import java.util.regex.Pattern;

import org.dhole.http.HttpStatus;

/**
 * An expected application error answered with its status and the error envelope
 * {@code {"error": {"code", "message", "requestId"}}}. The message is sent to the client: it must be
 * safe to show and must not contain secrets or input values.
 *
 * <pre>{@code
 * public class InsufficientBalance extends AppException {
 *     public InsufficientBalance() {
 *         super("INSUFFICIENT_BALANCE", HttpStatus.CONFLICT, "Insufficient balance.");
 *     }
 * }
 * }</pre>
 */
public class AppException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]*");

    private final String code;
    private final HttpStatus status;

    /**
     * @param code stable machine-readable code, upper case with underscores, for example
     *        {@code INSUFFICIENT_BALANCE}
     */
    public AppException(String code, HttpStatus status, String message) {
        super(Objects.requireNonNull(message, "message"));
        Objects.requireNonNull(code, "code");
        if (!CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("Error code must be upper case with underscores: " + code);
        }
        this.code = code;
        this.status = Objects.requireNonNull(status, "status");
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
