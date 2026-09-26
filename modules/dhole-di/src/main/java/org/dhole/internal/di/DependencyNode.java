package org.dhole.internal.di;

import java.util.List;
import java.util.Objects;

/**
 * One requested type in a {@link DependencyGraph}, the definition that provides it and the nodes
 * of its dependencies, in constructor order. Immutable.
 */
final class DependencyNode {

    private final Class<?> requestedType;
    private final ComponentDefinition definition;
    private final List<DependencyNode> dependencies;

    DependencyNode(Class<?> requestedType, ComponentDefinition definition, List<DependencyNode> dependencies) {
        this.requestedType = Objects.requireNonNull(requestedType, "requestedType");
        this.definition = Objects.requireNonNull(definition, "definition");
        this.dependencies = List.copyOf(dependencies);
    }

    Class<?> requestedType() {
        return requestedType;
    }

    ComponentDefinition definition() {
        return definition;
    }

    List<DependencyNode> dependencies() {
        return dependencies;
    }

    /**
     * Returns the requested type, followed by the providing type when they differ, for example
     * {@code PaymentGateway (StripePaymentGateway)} or {@code HttpClient (factory)}.
     */
    String label() {
        String name = DependencyMessages.name(requestedType);
        return switch (definition.kind()) {
            case FACTORY -> name + " (factory)";
            case INSTANCE -> name + " (instance)";
            case CONSTRUCTOR -> definition.type() == requestedType
                    ? name
                    : name + " (" + DependencyMessages.name(definition.type()) + ")";
        };
    }

    @Override
    public String toString() {
        return "DependencyNode[" + label() + "]";
    }
}
