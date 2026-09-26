package org.dhole.di;

/**
 * A component cannot be resolved or constructed: missing provider, unusable constructor,
 * failing constructor or factory, or failing cleanup.
 */
public class DependencyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DependencyException(String message) {
        super(message);
    }

    public DependencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
