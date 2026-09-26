package org.dhole.internal.di;

import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.dhole.di.AmbiguousDependencyException;
import org.dhole.di.DependencyException;

/**
 * Registered components and bindings, and the rules that turn a requested type into a
 * {@link ComponentDefinition}. Creates no instances.
 *
 * <p>Resolution of a requested type:
 * <ol>
 *   <li>an explicit binding for the type wins;</li>
 *   <li>a concrete class is constructed from its single eligible constructor;</li>
 *   <li>an interface or abstract class resolves to the only registered component assignable to
 *       it; none is a missing provider, several are ambiguous.</li>
 * </ol>
 * Registration order never selects between candidates.
 */
final class ComponentRegistry {

    private final List<Class<?>> registrations;
    private final Map<Class<?>, Binding> bindings;
    private final Map<Class<?>, ComponentScope> components;
    private final Map<Class<?>, ComponentDefinition> bound = new LinkedHashMap<>();
    private final Map<Class<?>, ComponentDefinition> constructed = new ConcurrentHashMap<>();

    ComponentRegistry(List<Class<?>> registrations, Map<Class<?>, Binding> bindings,
            Map<Class<?>, ComponentScope> components) {
        this.registrations = List.copyOf(registrations);
        this.bindings = Map.copyOf(bindings);
        this.components = Map.copyOf(components);
        bindings.values().forEach(binding -> {
            if (binding instanceof Binding.ToInstance instance) {
                bound.put(instance.type(), ComponentDefinition.instance(instance.type(), instance.instance(),
                        instance.owned()));
            } else if (binding instanceof Binding.ToFactory factory) {
                bound.put(factory.type(), ComponentDefinition.factory(factory.type(), factory.scope(),
                        factory.factory()));
            }
        });
    }

    /**
     * Returns every registered type (components and bound types) in registration order.
     */
    List<Class<?>> registrations() {
        return registrations;
    }

    /**
     * Returns the definition that provides {@code requested}.
     *
     * @param path the dependency path ending with {@code requested}, for diagnostics
     * @throws DependencyException if no single usable provider exists
     */
    ComponentDefinition definition(Class<?> requested, List<Class<?>> path) {
        Binding binding = bindings.get(requested);
        if (binding instanceof Binding.ToClass toClass) {
            return constructed(toClass.implementation(), path);
        }
        if (binding != null) {
            return bound.get(requested);
        }
        if (requested.isPrimitive() || requested.isArray()) {
            throw new DependencyException(DependencyMessages.unusable(requested,
                    DependencyMessages.name(requested) + " cannot be injected.", path));
        }
        if (requested.isInterface() || Modifier.isAbstract(requested.getModifiers())) {
            List<Class<?>> candidates = components.keySet().stream()
                    .filter(requested::isAssignableFrom)
                    .sorted(Comparator.comparing(Class::getName))
                    .toList();
            if (candidates.isEmpty()) {
                throw new DependencyException(DependencyMessages.missing(requested, path));
            }
            if (candidates.size() > 1) {
                throw new AmbiguousDependencyException(DependencyMessages.ambiguous(requested, candidates, path));
            }
            return constructed(candidates.get(0), path);
        }
        return constructed(requested, path);
    }

    private ComponentDefinition constructed(Class<?> type, List<Class<?>> path) {
        try {
            return constructed.computeIfAbsent(type,
                    t -> ConstructorDefinitions.of(t, components.getOrDefault(t, ComponentScope.SINGLETON)));
        } catch (UnusableComponentException e) {
            throw new DependencyException(DependencyMessages.unusable(type, e.getMessage(), path));
        }
    }
}
