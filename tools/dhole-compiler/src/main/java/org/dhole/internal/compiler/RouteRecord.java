package org.dhole.internal.compiler;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Binding metadata of one typed route, as written to {@code routes.idx}.
 *
 * @param controller binary name of the controller; with {@code method} and {@code path}, the stable
 *        route identity
 * @param handler name of the controller method referenced by {@code this::handler}
 * @param response the declared generic return type, or {@code void}
 */
record RouteRecord(
        String controller,
        String method,
        String path,
        String handler,
        List<Parameter> parameters,
        String response,
        Optional<SourceLocation> source) {

    RouteRecord {
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(handler, "handler");
        parameters = List.copyOf(parameters);
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(source, "source");
    }

    /**
     * @param source one of {@code PATH}, {@code QUERY}, {@code HEADER}, {@code BODY}, {@code REQUEST}
     * @param type the declared generic type
     */
    record Parameter(String name, String source, String type) {
    }
}
