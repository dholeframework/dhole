package org.dhole.internal.di;

/**
 * Where a component definition comes from, for diagnostics.
 */
enum ComponentOrigin {

    /**
     * An application class constructed through its constructor, or an application instance.
     */
    APPLICATION,

    /**
     * A factory binding.
     */
    FACTORY
}
