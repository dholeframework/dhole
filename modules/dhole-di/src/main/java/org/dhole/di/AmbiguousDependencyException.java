package org.dhole.di;

/**
 * More than one provider can satisfy a dependency and no explicit binding selects one.
 */
public class AmbiguousDependencyException extends DependencyException {

    private static final long serialVersionUID = 1L;

    public AmbiguousDependencyException(String message) {
        super(message);
    }
}
