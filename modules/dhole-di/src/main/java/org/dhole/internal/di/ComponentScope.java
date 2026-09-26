package org.dhole.internal.di;

/**
 * Lifetime of a component instance. {@code REQUEST} arrives with HTTP.
 */
enum ComponentScope {

    /**
     * One instance per container. The default scope.
     */
    SINGLETON,

    /**
     * A new instance every time the component is resolved.
     */
    PROTOTYPE
}
