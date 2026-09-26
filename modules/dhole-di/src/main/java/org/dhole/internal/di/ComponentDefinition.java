package org.dhole.internal.di;

import java.util.List;
import java.util.Objects;

/**
 * How one component is created: its type, scope, origin, statically known dependencies and
 * instantiator.
 *
 * <p>The container only works with definitions. M3 builds constructor definitions by reflection
 * ({@link ConstructorDefinitions}); generated metadata can later supply the same definitions.
 *
 * @param type the component type: the implementation for constructor components, the provided
 *        type for factory and instance bindings
 * @param description diagnostic form, for example {@code UserService(UserRepository)}
 * @param dependencies the dependencies passed to the instantiator, in order; empty for factories,
 *        which resolve their dependencies through {@link FactoryContext}
 * @param owned whether the container owns (and closes) instances that are {@link AutoCloseable}
 */
record ComponentDefinition(
        Class<?> type,
        Kind kind,
        ComponentScope scope,
        ComponentOrigin origin,
        String description,
        List<Class<?>> dependencies,
        Instantiator instantiator,
        boolean owned) {

    enum Kind {
        CONSTRUCTOR,
        FACTORY,
        INSTANCE
    }

    @FunctionalInterface
    interface Instantiator {

        Object create(List<Object> dependencies, FactoryContext context) throws Exception;
    }

    ComponentDefinition {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(description, "description");
        dependencies = List.copyOf(dependencies);
        Objects.requireNonNull(instantiator, "instantiator");
    }

    static ComponentDefinition factory(Class<?> type, ComponentScope scope, Factory<?> factory) {
        return new ComponentDefinition(type, Kind.FACTORY, scope, ComponentOrigin.FACTORY,
                "factory for " + DependencyMessages.name(type), List.of(),
                (dependencies, context) -> factory.create(context), true);
    }

    static ComponentDefinition instance(Class<?> type, Object instance, boolean owned) {
        return new ComponentDefinition(type, Kind.INSTANCE, ComponentScope.SINGLETON, ComponentOrigin.APPLICATION,
                "instance of " + DependencyMessages.name(type), List.of(),
                (dependencies, context) -> instance, owned);
    }
}
