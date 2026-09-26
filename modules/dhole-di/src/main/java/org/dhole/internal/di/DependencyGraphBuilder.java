package org.dhole.internal.di;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.dhole.di.CircularDependencyException;

/**
 * Builds and validates a {@link DependencyGraph} from a {@link ComponentRegistry} without creating
 * instances. Missing providers, ambiguous providers, unusable constructors and cycles fail here.
 *
 * <p>Factories are leaves: they resolve their dependencies at creation time, where the container
 * guards against cycles as well.
 */
final class DependencyGraphBuilder {

    private final ComponentRegistry registry;
    private final Map<Class<?>, DependencyNode> nodes = new HashMap<>();

    DependencyGraphBuilder(ComponentRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * @throws org.dhole.di.DependencyException if any root cannot be satisfied
     */
    DependencyGraph build(List<Class<?>> roots) {
        List<DependencyNode> rootNodes = new ArrayList<>();
        for (Class<?> root : roots) {
            rootNodes.add(visit(root, new ArrayList<>(), new ArrayList<>()));
        }
        return new DependencyGraph(rootNodes, nodes);
    }

    private DependencyNode visit(Class<?> requested, List<Class<?>> path, List<Class<?>> components) {
        DependencyNode known = nodes.get(requested);
        if (known != null) {
            return known;
        }
        path.add(requested);
        ComponentDefinition definition = registry.definition(requested, List.copyOf(path));
        int cycleStart = components.indexOf(definition.type());
        if (cycleStart >= 0) {
            throw new CircularDependencyException(DependencyMessages.circular(path.subList(cycleStart, path.size())));
        }
        components.add(definition.type());
        List<DependencyNode> dependencies = new ArrayList<>();
        for (Class<?> dependency : definition.dependencies()) {
            dependencies.add(visit(dependency, path, components));
        }
        components.remove(components.size() - 1);
        path.remove(path.size() - 1);
        DependencyNode node = new DependencyNode(requested, definition, dependencies);
        nodes.put(requested, node);
        return node;
    }
}
