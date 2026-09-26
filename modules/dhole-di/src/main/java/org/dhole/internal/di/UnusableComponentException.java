package org.dhole.internal.di;

/**
 * A type cannot be used as a component. Carries only the problem; the dependency path is added
 * when it is reported as a {@link org.dhole.di.DependencyException}.
 */
final class UnusableComponentException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    UnusableComponentException(String problem) {
        super(problem);
    }
}
