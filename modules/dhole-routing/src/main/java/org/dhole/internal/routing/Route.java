package org.dhole.internal.routing;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.dhole.http.HttpMethod;
import org.dhole.routing.Handler;
import org.dhole.routing.Middleware;
import org.dhole.routing.RouteDefinition;

/**
 * A registered route: method, template, either a raw {@link Handler} or a {@link TypedHandler}, the
 * middleware that applies to it (outermost first) and the controller that declared it.
 */
public final class Route implements RouteDefinition {

    private final HttpMethod method;
    private final RouteTemplate template;
    private final Handler handler;
    private final TypedHandler typed;
    private final List<Middleware> middleware;
    private final String controller;

    Route(HttpMethod method, RouteTemplate template, Handler handler, TypedHandler typed, List<Middleware> middleware,
            String controller) {
        this.method = Objects.requireNonNull(method, "method");
        this.template = Objects.requireNonNull(template, "template");
        if ((handler == null) == (typed == null)) {
            throw new IllegalArgumentException("A route has exactly one handler");
        }
        this.handler = handler;
        this.typed = typed;
        this.middleware = List.copyOf(middleware);
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    @Override
    public HttpMethod method() {
        return method;
    }

    @Override
    public String path() {
        return template.text();
    }

    /**
     * Returns the raw request handler, or {@code null} for a typed route.
     */
    public Handler handler() {
        return handler;
    }

    /**
     * Returns the typed handler of a route registered with {@code RouteBuilder.to(...)}.
     */
    public Optional<TypedHandler> typed() {
        return Optional.ofNullable(typed);
    }

    /**
     * Returns the middleware applying to this route, outermost first.
     */
    public List<Middleware> middleware() {
        return middleware;
    }

    /**
     * Returns the name of the declaring controller: its binary class name when registered by the
     * web runtime; the stable route identity is {@code controller + method + path}.
     */
    public String controller() {
        return controller;
    }

    /**
     * Returns the simple name of the declaring controller, for diagnostics.
     */
    public String declaredBy() {
        String simple = controller.substring(controller.lastIndexOf('.') + 1);
        return simple.substring(simple.lastIndexOf('$') + 1);
    }

    RouteTemplate template() {
        return template;
    }

    @Override
    public String toString() {
        return method + " " + template.text();
    }
}
