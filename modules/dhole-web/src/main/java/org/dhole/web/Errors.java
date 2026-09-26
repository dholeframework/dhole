package org.dhole.web;

import org.dhole.http.HttpStatus;

/**
 * Common application errors.
 *
 * <pre>{@code
 * return users.find(id).orElseThrow(() -> Errors.notFound("User"));
 * }</pre>
 */
public final class Errors {

    private Errors() {
    }

    /**
     * {@code 404 NOT_FOUND} with the message {@code "<resource> not found."}.
     */
    public static AppException notFound(String resource) {
        return new AppException("NOT_FOUND", HttpStatus.NOT_FOUND, resource + " not found.");
    }

    public static AppException badRequest(String message) {
        return new AppException("BAD_REQUEST", HttpStatus.BAD_REQUEST, message);
    }

    public static AppException unauthorized(String message) {
        return new AppException("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, message);
    }

    public static AppException forbidden(String message) {
        return new AppException("FORBIDDEN", HttpStatus.FORBIDDEN, message);
    }

    public static AppException conflict(String message) {
        return new AppException("CONFLICT", HttpStatus.CONFLICT, message);
    }
}
