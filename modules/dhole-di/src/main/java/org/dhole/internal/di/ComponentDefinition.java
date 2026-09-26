package org.dhole.internal.di;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * How one component is created: its type, scope, origin, statically known dependencies and
 * instantiator.
 *
 * <p>The container only works with definitions. Constructor definitions come from build-time
 * component metadata or, for types outside the index, controlled reflection
 * ({@link ConstructorDefinitions}).
 *
 * @param type the component type: the implementation for constructor components, the provided
 *        type for factory and instance bindings
 * @param description diagnostic form, for example {@code UserService(UserRepository)}
 * @param dependencies the dependencies passed to the instantiator, in order; empty for factories,
 *        which resolve their dependencies through {@link FactoryContext}
 * @param owned whether the container owns (and closes) instances that are {@link AutoCloseable}
 * @param location the source location recorded by build-time metadata, for diagnostics
 */
record ComponentDefinition(
        Class<?> type,
        Kind kind,
        ComponentScope scope,
        ComponentOrigin origin,
        String description,
        List<Class<?>> dependencies,
        Instantiator instantiator,
        boolean owned,
        Optional<String> location) {

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
        Objects.requireNonNull(location, "location");
    }

    static ComponentDefinition factory(Class<?> type, ComponentScope scope, Factory<?> factory) {
        return new ComponentDefinition(type, Kind.FACTORY, scope, ComponentOrigin.FACTORY,
                "factory for " + DependencyMessages.name(type), List.of(),
                (dependencies, context) -> factory.create(context), true, Optional.empty());
    }

    static ComponentDefinition instance(Class<?> type, Object instance, boolean owned) {
        return new ComponentDefinition(type, Kind.INSTANCE, ComponentScope.SINGLETON, ComponentOrigin.APPLICATION,
                "instance of " + DependencyMessages.name(type), List.of(),
                (dependencies, context) -> instance, owned, Optional.empty());
    }
}
