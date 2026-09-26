package org.dhole.internal.routing;

import java.util.List;
import java.util.Objects;

import org.dhole.http.HttpMethod;
import org.dhole.routing.Handler;
import org.dhole.routing.Middleware;
import org.dhole.routing.RouteDefinition;

/**
 * A registered route: method, template, handler, the middleware that applies to it (outermost
 * first) and who declared it.
 */
public final class Route implements RouteDefinition {

    private final HttpMethod method;
    private final RouteTemplate template;
    private final Handler handler;
    private final List<Middleware> middleware;
    private final String declaredBy;

    Route(HttpMethod method, RouteTemplate template, Handler handler, List<Middleware> middleware, String declaredBy) {
        this.method = Objects.requireNonNull(method, "method");
        this.template = Objects.requireNonNull(template, "template");
        this.handler = Objects.requireNonNull(handler, "handler");
        this.middleware = List.copyOf(middleware);
        this.declaredBy = Objects.requireNonNull(declaredBy, "declaredBy");
    }

    @Override
    public HttpMethod method() {
        return method;
    }

    @Override
    public String path() {
        return template.text();
    }

    public Handler handler() {
        return handler;
    }

    /**
     * Returns the middleware applying to this route, outermost first.
     */
    public List<Middleware> middleware() {
        return middleware;
    }

    public String declaredBy() {
        return declaredBy;
    }

    RouteTemplate template() {
        return template;
    }

    @Override
    public String toString() {
        return method + " " + template.text();
    }
}
