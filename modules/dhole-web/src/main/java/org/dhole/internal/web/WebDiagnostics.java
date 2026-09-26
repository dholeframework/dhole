package org.dhole.internal.web;

import java.util.ArrayList;
import java.util.List;

import org.dhole.internal.di.ComponentMetadata;
import org.dhole.internal.di.ContainerBuilder;
import org.dhole.internal.di.DependencyContainer;
import org.dhole.internal.validation.ValidationMetadata;
import org.dhole.validation.ValidationResult;
import org.dhole.validation.Validator;

/**
 * Read-only checks of a compiled application for {@code dhole doctor}: every metadata index is
 * readable and the dependency graph of every controller resolves. Nothing is instantiated, no
 * server is started. Only JDK types cross this boundary, so tooling in another class loader can
 * call {@link #check(ClassLoader)} reflectively.
 *
 * <p>Public only for Dhole tooling; not application API.
 */
public final class WebDiagnostics {

    private WebDiagnostics() {
    }

    /**
     * @return one line per check, each starting with {@code ok } or {@code error }
     */
    public static List<String> check(ClassLoader loader) {
        List<String> results = new ArrayList<>();
        ComponentMetadata components;
        try {
            components = ComponentMetadata.load(loader);
            results.add("ok components.idx readable");
        } catch (RuntimeException e) {
            results.add("error " + e.getMessage());
            return results;
        }
        try {
            RouteMetadata.load(loader);
            results.add("ok routes.idx readable");
        } catch (RuntimeException e) {
            results.add("error " + e.getMessage());
        }
        try {
            ValidationMetadata.load(loader);
            results.add("ok validation.idx readable");
        } catch (RuntimeException e) {
            results.add("error " + e.getMessage());
        }
        List<String> controllers = components.providersOf(WebRuntime.CONTROLLER);
        try (DependencyContainer container = ContainerBuilder.create().metadata(components)
                .bind(Validator.class).toInstance(value -> ValidationResult.valid()).build()) {
            List<String> problems = new ArrayList<>();
            for (String controller : controllers) {
                try {
                    container.graph(components.loadClass(controller));
                } catch (RuntimeException e) {
                    problems.add(e.getMessage());
                }
            }
            if (problems.isEmpty()) {
                results.add("ok dependency graph valid (" + controllers.size() + " controller"
                        + (controllers.size() == 1 ? "" : "s") + ")");
            } else {
                problems.forEach(problem -> results.add("error " + problem));
            }
        } catch (RuntimeException e) {
            results.add("error " + e.getMessage());
        }
        return results;
    }
}
