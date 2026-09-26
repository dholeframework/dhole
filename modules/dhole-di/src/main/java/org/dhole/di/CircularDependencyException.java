package org.dhole.di;

/**
 * Components depend on each other in a cycle. Cycles are always errors.
 */
public class CircularDependencyException extends DependencyException {

    private static final long serialVersionUID = 1L;

    public CircularDependencyException(String message) {
        super(message);
    }
}
