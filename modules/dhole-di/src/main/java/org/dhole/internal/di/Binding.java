package org.dhole.internal.di;

/**
 * An explicit provider for a type: an implementation class, an instance or a factory.
 */
sealed interface Binding {

    Class<?> type();

    /**
     * {@code bind(type).to(implementation)}.
     */
    record ToClass(Class<?> type, Class<?> implementation) implements Binding {
    }

    /**
     * {@code bind(type).toInstance(instance)}; the container owns the instance only when
     * ownership was transferred explicitly.
     */
    record ToInstance(Class<?> type, Object instance, boolean owned) implements Binding {

        @Override
        public String toString() {
            return "ToInstance[type=" + type.getName() + ", owned=" + owned + "]";
        }
    }

    /**
     * {@code provide(type, factory)}.
     */
    record ToFactory(Class<?> type, ComponentScope scope, Factory<?> factory) implements Binding {
    }
}
