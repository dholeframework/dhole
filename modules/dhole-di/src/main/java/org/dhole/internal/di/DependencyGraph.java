package org.dhole.internal.di;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Validated, acyclic dependency graph for a set of root types. Immutable and inspectable.
 */
final class DependencyGraph {

    private final List<DependencyNode> roots;
    private final Map<Class<?>, DependencyNode> nodes;

    DependencyGraph(List<DependencyNode> roots, Map<Class<?>, DependencyNode> nodes) {
        this.roots = List.copyOf(roots);
        this.nodes = Map.copyOf(nodes);
    }

    List<DependencyNode> roots() {
        return roots;
    }

    /**
     * Returns the node of a requested type anywhere in the graph.
     */
    Optional<DependencyNode> node(Class<?> requestedType) {
        return Optional.ofNullable(nodes.get(requestedType));
    }

    /**
     * Returns every node once, dependencies before their dependents, in root order.
     */
    List<DependencyNode> dependencyOrder() {
        Set<DependencyNode> ordered = new LinkedHashSet<>();
        roots.forEach(root -> addInDependencyOrder(root, ordered));
        return List.copyOf(ordered);
    }

    /**
     * Renders each root as a tree:
     *
     * <pre>
     * UserController
     * ├── UserService
     * │   └── UserRepository
     * └── Mail
     * </pre>
     */
    String render() {
        List<String> lines = new ArrayList<>();
        for (DependencyNode root : roots) {
            lines.add(root.label());
            renderChildren(root, "", lines);
        }
        return String.join("\n", lines);
    }

    private static void addInDependencyOrder(DependencyNode node, Set<DependencyNode> ordered) {
        if (ordered.contains(node)) {
            return;
        }
        node.dependencies().forEach(dependency -> addInDependencyOrder(dependency, ordered));
        ordered.add(node);
    }

    private static void renderChildren(DependencyNode node, String indent, List<String> lines) {
        List<DependencyNode> children = node.dependencies();
        for (int index = 0; index < children.size(); index++) {
            boolean last = index == children.size() - 1;
            DependencyNode child = children.get(index);
            lines.add(indent + (last ? "└── " : "├── ") + child.label());
            renderChildren(child, indent + (last ? "    " : "│   "), lines);
        }
    }
}
