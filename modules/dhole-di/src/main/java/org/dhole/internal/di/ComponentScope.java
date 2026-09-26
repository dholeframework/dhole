package org.dhole.internal.di;

/**
 * Lifetime of a component instance.
 */
public enum ComponentScope {

    /**
     * One instance per container. The default scope.
     */
    SINGLETON,

    /**
     * One instance per request scope, created only inside a request and closed with it.
     */
    REQUEST,

    /**
     * A new instance every time the component is resolved.
     */
    PROTOTYPE
}
