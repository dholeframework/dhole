package org.dhole.internal.routing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.dhole.http.HttpMethod;
import org.dhole.routing.Handler;
import org.dhole.routing.Handler0;
import org.dhole.routing.Handler1;
import org.dhole.routing.Handler2;
import org.dhole.routing.Handler3;
import org.dhole.routing.Middleware;
import org.dhole.routing.RouteBuilder;
import org.dhole.routing.RouteDefinition;
import org.dhole.routing.Router;
import org.dhole.routing.RoutingException;

/**
 * Collects routes from {@link Router}s at startup and builds the {@link RouteMatcher} once every
 * route is known, rejecting duplicate routes and typed routes left without a handler. Registration
 * is closed after {@link #build()}.
 */
public final class RouteRegistry {

    private final List<Route> routes = new ArrayList<>();
    private final List<Builder> pending = new ArrayList<>();
    private boolean built;

    /**
     * Returns a root router for one controller.
     *
     * @param controller the declaring controller's name; the web runtime passes the binary class
     *        name, which forms the stable route identity {@code controller + method + path}
     */
    public Router router(String controller) {
        Objects.requireNonNull(controller, "controller");
        return new Group(this, "", List.of(), controller);
    }

    /**
     * Validates every route and returns the matcher.
     *
     * @throws RoutingException if a typed route has no handler or two routes have the same method
     *         and path shape
     */
    public synchronized RouteMatcher build() {
        built = true;
        for (Builder builder : pending) {
            if (!builder.completed) {
                throw new RoutingException("Routing Error\n\nRoute " + builder.method + " " + builder.template.text()
                        + " declared by " + builder.controller + " has no handler.\n\nComplete it with .to(...).");
            }
        }
        Map<String, List<Route>> byKey = new TreeMap<>();
        for (Route route : routes) {
            byKey.computeIfAbsent(route.method() + " " + route.template().shape(), key -> new ArrayList<>()).add(route);
        }
        for (List<Route> same : byKey.values()) {
            if (same.size() > 1) {
                List<Route> sorted = same.stream()
                        .sorted(Comparator.comparing(Route::declaredBy).thenComparing(Route::path))
                        .toList();
                StringBuilder message = new StringBuilder("Routing Error\n\nDuplicate route:\n")
                        .append(sorted.get(0)).append("\n\nDeclared by:");
                sorted.forEach(route -> message.append('\n').append(route.declaredBy())
                        .append(route.path().equals(sorted.get(0).path()) ? "" : " (" + route.path() + ")"));
                throw new RoutingException(message.toString());
            }
        }
        return new RouteMatcher(routes);
    }

    private synchronized void add(Route route) {
        if (built) {
            throw new RoutingException("Routing Error\n\nRoutes can only be registered during startup:\n" + route);
        }
        routes.add(route);
    }

    private synchronized void track(Builder builder) {
        if (built) {
            throw new RoutingException("Routing Error\n\nRoutes can only be registered during startup:\n"
                    + builder.method + " " + builder.template.text());
        }
        pending.add(builder);
    }

    /**
     * A router for one group: its prefix and the middleware inherited from enclosing groups.
     */
    private static final class Group implements Router {

        private final RouteRegistry registry;
        private final String prefix;
        private final List<Middleware> middleware;
        private final String controller;
        private boolean declared;

        Group(RouteRegistry registry, String prefix, List<Middleware> middleware, String controller) {
            this.registry = registry;
            this.prefix = prefix;
            this.middleware = new ArrayList<>(middleware);
            this.controller = controller;
        }

        @Override
        public RouteDefinition get(String path, Handler handler) {
            return add(HttpMethod.GET, path, handler);
        }

        @Override
        public RouteDefinition post(String path, Handler handler) {
            return add(HttpMethod.POST, path, handler);
        }

        @Override
        public RouteDefinition put(String path, Handler handler) {
            return add(HttpMethod.PUT, path, handler);
        }

        @Override
        public RouteDefinition patch(String path, Handler handler) {
            return add(HttpMethod.PATCH, path, handler);
        }

        @Override
        public RouteDefinition delete(String path, Handler handler) {
            return add(HttpMethod.DELETE, path, handler);
        }

        @Override
        public RouteBuilder get(String path) {
            return builder(HttpMethod.GET, path);
        }

        @Override
        public RouteBuilder post(String path) {
            return builder(HttpMethod.POST, path);
        }

        @Override
        public RouteBuilder put(String path) {
            return builder(HttpMethod.PUT, path);
        }

        @Override
        public RouteBuilder patch(String path) {
            return builder(HttpMethod.PATCH, path);
        }

        @Override
        public RouteBuilder delete(String path) {
            return builder(HttpMethod.DELETE, path);
        }

        @Override
        public void group(String prefix, Consumer<Router> routes) {
            Objects.requireNonNull(routes, "routes");
            String joined = RouteTemplate.join(this.prefix, prefix);
            RouteTemplate.parse(joined);
            declared = true;
            routes.accept(new Group(registry, joined.equals("/") ? "" : joined, middleware, controller));
        }

        @Override
        public void use(Middleware middleware) {
            Objects.requireNonNull(middleware, "middleware");
            if (declared) {
                throw new RoutingException("Routing Error\n\nMiddleware must be declared before the routes and groups "
                        + "it applies to" + (prefix.isEmpty() ? "." : " in group " + prefix + "."));
            }
            this.middleware.add(middleware);
        }

        private RouteDefinition add(HttpMethod method, String path, Handler handler) {
            Objects.requireNonNull(handler, "handler");
            Route route = new Route(method, template(path), handler, null, middleware, controller);
            declared = true;
            registry.add(route);
            return route;
        }

        private RouteBuilder builder(HttpMethod method, String path) {
            Builder builder = new Builder(registry, method, template(path), List.copyOf(middleware), controller);
            declared = true;
            registry.track(builder);
            return builder;
        }

        private RouteTemplate template(String path) {
            return RouteTemplate.parse(RouteTemplate.join(prefix, path));
        }
    }

    private static final class Builder implements RouteBuilder {

        private final RouteRegistry registry;
        private final HttpMethod method;
        private final RouteTemplate template;
        private final List<Middleware> middleware;
        private final String controller;
        private boolean completed;

        Builder(RouteRegistry registry, HttpMethod method, RouteTemplate template, List<Middleware> middleware,
                String controller) {
            this.registry = registry;
            this.method = method;
            this.template = template;
            this.middleware = middleware;
            this.controller = controller;
        }

        @Override
        public RouteDefinition to(Handler0<?> handler) {
            return complete(TypedHandler.of(Objects.requireNonNull(handler, "handler")));
        }

        @Override
        public <A> RouteDefinition to(Handler1<A, ?> handler) {
            return complete(TypedHandler.of(Objects.requireNonNull(handler, "handler")));
        }

        @Override
        public <A, B> RouteDefinition to(Handler2<A, B, ?> handler) {
            return complete(TypedHandler.of(Objects.requireNonNull(handler, "handler")));
        }

        @Override
        public <A, B, C> RouteDefinition to(Handler3<A, B, C, ?> handler) {
            return complete(TypedHandler.of(Objects.requireNonNull(handler, "handler")));
        }

        private RouteDefinition complete(TypedHandler handler) {
            if (completed) {
                throw new RoutingException("Routing Error\n\nRoute " + method + " " + template.text()
                        + " already has a handler.");
            }
            Route route = new Route(method, template, null, handler, middleware, controller);
            registry.add(route);
            completed = true;
            return route;
        }
    }
}
