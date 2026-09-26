package org.dhole.internal.di;

/**
 * Lets a {@link Factory} resolve the components it needs.
 */
public interface FactoryContext {

    <T> T resolve(Class<T> type);
}
