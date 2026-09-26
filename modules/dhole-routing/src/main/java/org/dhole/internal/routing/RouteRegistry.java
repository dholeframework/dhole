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
import org.dhole.routing.Middleware;
import org.dhole.routing.RouteDefinition;
import org.dhole.routing.Router;
import org.dhole.routing.RoutingException;

/**
 * Collects routes from {@link Router}s at startup and builds the {@link RouteMatcher} once every
 * route is known, rejecting duplicate routes. Registration is closed after {@link #build()}.
 */
public final class RouteRegistry {

    private final List<Route> routes = new ArrayList<>();
    private boolean built;

    /**
     * Returns a root router whose routes are reported as declared by {@code declaredBy}, for
     * example a controller name.
     */
    public Router router(String declaredBy) {
        Objects.requireNonNull(declaredBy, "declaredBy");
        return new Group(this, "", List.of(), declaredBy);
    }

    /**
     * Validates every route and returns the matcher.
     *
     * @throws RoutingException if two routes have the same method and path shape
     */
    public synchronized RouteMatcher build() {
        built = true;
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

    /**
     * A router for one group: its prefix and the middleware inherited from enclosing groups.
     */
    private static final class Group implements Router {

        private final RouteRegistry registry;
        private final String prefix;
        private final List<Middleware> middleware;
        private final String declaredBy;
        private boolean declared;

        Group(RouteRegistry registry, String prefix, List<Middleware> middleware, String declaredBy) {
            this.registry = registry;
            this.prefix = prefix;
            this.middleware = new ArrayList<>(middleware);
            this.declaredBy = declaredBy;
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
        public void group(String prefix, Consumer<Router> routes) {
            Objects.requireNonNull(routes, "routes");
            String joined = RouteTemplate.join(this.prefix, prefix);
            RouteTemplate.parse(joined);
            declared = true;
            routes.accept(new Group(registry, joined.equals("/") ? "" : joined, middleware, declaredBy));
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
            RouteTemplate template = RouteTemplate.parse(RouteTemplate.join(prefix, path));
            Route route = new Route(method, template, handler, middleware, declaredBy);
            declared = true;
            registry.add(route);
            return route;
        }
    }
}
