package org.dhole.internal.di;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Formats dependency diagnostics.
 */
final class DependencyMessages {

    private DependencyMessages() {
    }

    static String name(Class<?> type) {
        return type.getSimpleName();
    }

    /**
     * Formats a dependency path:
     *
     * <pre>
     * UserController
     *   -&gt; UserService
     *       -&gt; UserRepository
     * </pre>
     */
    static String path(List<Class<?>> path) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < path.size(); index++) {
            if (index > 0) {
                text.append('\n').append("  ").append("    ".repeat(index - 1)).append("-> ");
            }
            text.append(name(path.get(index)));
        }
        return text.toString();
    }

    static String missing(Class<?> type, List<Class<?>> path) {
        return "Dependency Error\n\nNo provider found for " + name(type) + "." + pathSection(path);
    }

    static String ambiguous(Class<?> type, List<Class<?>> candidates, List<Class<?>> path) {
        return "Dependency Error\n\nMultiple providers found for " + name(type) + ":\n\n"
                + candidates.stream().map(candidate -> "- " + name(candidate)).collect(Collectors.joining("\n"))
                + pathSection(path) + "\n\nDeclare an explicit binding.";
    }

    static String unusable(Class<?> type, String problem, List<Class<?>> path) {
        return "Dependency Error\n\nCannot create " + name(type) + ".\n\nProblem:\n" + problem + pathSection(path);
    }

    static String circular(List<Class<?>> cycle) {
        return "Circular Dependency\n\n" + path(cycle);
    }

    static String constructionFailed(ComponentDefinition definition, List<Class<?>> path) {
        return "Dependency Error\n\nCould not construct " + name(definition.type()) + ".\n\n"
                + (definition.kind() == ComponentDefinition.Kind.CONSTRUCTOR ? "Constructor:\n" : "Provider:\n")
                + definition.description() + pathSection(path);
    }

    private static String pathSection(List<Class<?>> path) {
        return path.size() < 2 ? "" : "\n\nDependency path:\n" + path(path);
    }
}
