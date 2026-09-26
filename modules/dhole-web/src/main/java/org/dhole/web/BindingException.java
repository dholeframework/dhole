package org.dhole.web;

/**
 * A request value cannot be bound to a handler parameter: missing, not convertible to the parameter
 * type, or an unreadable body. Answered with {@code 400 Bad Request}. Messages name the parameter
 * and its source, never the received value.
 */
public class BindingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BindingException(String message) {
        super(message);
    }

    public BindingException(String message, Throwable cause) {
        super(message, cause);
    }
}
