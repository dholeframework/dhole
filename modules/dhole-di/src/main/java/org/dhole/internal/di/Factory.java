package org.dhole.internal.di;

/**
 * Creates a component that cannot, or should not, be built from its constructor, typically a
 * third-party type. Factories are infrastructure, not business logic.
 *
 * @param <T> the provided type
 */
@FunctionalInterface
interface Factory<T> {

    /**
     * @return the instance, never {@code null}
     */
    T create(FactoryContext context) throws Exception;
}
