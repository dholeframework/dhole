package org.dhole.internal.di;

/**
 * Lets a {@link Factory} resolve the components it needs.
 */
interface FactoryContext {

    <T> T resolve(Class<T> type);
}
