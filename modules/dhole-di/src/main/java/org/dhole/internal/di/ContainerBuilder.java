package org.dhole.internal.di;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.dhole.di.DependencyException;

/**
 * Explicit registration of components and bindings.
 *
 * <pre>{@code
 * ContainerBuilder.create()
 *     .component(StripePaymentGateway.class)
 *     .bind(PaymentGateway.class).to(StripePaymentGateway.class)
 *     .bind(Clock.class).toInstance(Clock.systemUTC())
 *     .provide(HttpClient.class, context -> HttpClient.newHttpClient());
 * }</pre>
 *
 * Every type may have at most one binding; a second one is a binding conflict.
 */
final class ContainerBuilder {

    private final List<Class<?>> registrations = new ArrayList<>();
    private final Map<Class<?>, Binding> bindings = new LinkedHashMap<>();
    private final Map<Class<?>, ComponentScope> components = new LinkedHashMap<>();

    private ContainerBuilder() {
    }

    static ContainerBuilder create() {
        return new ContainerBuilder();
    }

    /**
     * Registers a concrete component with singleton scope.
     */
    ContainerBuilder component(Class<?> type) {
        return component(type, ComponentScope.SINGLETON);
    }

    /**
     * Registers a concrete component. Registered components are candidates for the interfaces and
     * abstract classes they implement.
     */
    ContainerBuilder component(Class<?> type, ComponentScope scope) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(scope, "scope");
        requireConcrete(type);
        if (components.putIfAbsent(type, scope) != null) {
            throw new DependencyException("Dependency Error\n\n" + DependencyMessages.name(type)
                    + " is registered more than once.");
        }
        registrations.add(type);
        return this;
    }

    <T> BindingBuilder<T> bind(Class<T> type) {
        Objects.requireNonNull(type, "type");
        return new BindingBuilder<>(type);
    }

    /**
     * Registers a singleton factory for {@code type}.
     */
    <T> ContainerBuilder provide(Class<T> type, Factory<? extends T> factory) {
        return provide(type, ComponentScope.SINGLETON, factory);
    }

    <T> ContainerBuilder provide(Class<T> type, ComponentScope scope, Factory<? extends T> factory) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(factory, "factory");
        return addBinding(new Binding.ToFactory(type, scope, factory));
    }

    ComponentRegistry registry() {
        return new ComponentRegistry(registrations, bindings, components);
    }

    private ContainerBuilder addBinding(Binding binding) {
        if (bindings.putIfAbsent(binding.type(), binding) != null) {
            throw new DependencyException("Binding Conflict\n\n" + DependencyMessages.name(binding.type())
                    + " has more than one binding.\n\nSelect a single binding explicitly.");
        }
        registrations.add(binding.type());
        return this;
    }

    private static void requireConcrete(Class<?> type) {
        try {
            ConstructorDefinitions.requireConstructible(type);
        } catch (UnusableComponentException e) {
            throw new DependencyException(DependencyMessages.unusable(type, e.getMessage(), List.of(type)));
        }
    }

    final class BindingBuilder<T> {

        private final Class<T> type;

        private BindingBuilder(Class<T> type) {
            this.type = type;
        }

        /**
         * Binds the type to a concrete implementation, constructed with its registered scope
         * (singleton when not registered).
         */
        ContainerBuilder to(Class<? extends T> implementation) {
            Objects.requireNonNull(implementation, "implementation");
            if (!type.isAssignableFrom(implementation)) {
                throw new DependencyException("Dependency Error\n\n" + DependencyMessages.name(implementation)
                        + " does not implement " + DependencyMessages.name(type) + ".");
            }
            requireConcrete(implementation);
            return addBinding(new Binding.ToClass(type, implementation));
        }

        /**
         * Binds the type to an external instance. The container does not own it and never
         * closes it.
         */
        ContainerBuilder toInstance(T instance) {
            return addBinding(new Binding.ToInstance(type, requireInstance(instance), false));
        }

        /**
         * Binds the type to an instance whose ownership is transferred to the container, which
         * closes it (if {@link AutoCloseable}) when the container is closed.
         */
        ContainerBuilder toOwnedInstance(T instance) {
            return addBinding(new Binding.ToInstance(type, requireInstance(instance), true));
        }

        private T requireInstance(T instance) {
            Objects.requireNonNull(instance, "instance");
            if (!type.isInstance(instance)) {
                throw new DependencyException("Dependency Error\n\nInstance is not a " + DependencyMessages.name(type) + ".");
            }
            return instance;
        }
    }
}
