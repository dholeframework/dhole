package org.dhole.routing;

import java.util.function.Consumer;

/**
 * Registers routes, without annotations.
 *
 * <pre>{@code
 * public void routes(Router routes) {
 *     routes.get("/hello", request -> "Hello World");
 *     routes.group("/api", api -> api.get("/health", request -> "OK"));
 * }
 * }</pre>
 *
 * The two-argument form registers a raw {@link Handler} that receives the {@link org.dhole.http.Request};
 * the one-argument form returns a {@link RouteBuilder} for a typed handler whose parameters are bound
 * from the request.
 *
 * Paths start with {@code /}; {@code {name}} marks a path parameter occupying a whole segment.
 * Registration happens once, at startup.
 */
public interface Router {

    RouteDefinition get(String path, Handler handler);

    RouteDefinition post(String path, Handler handler);

    RouteDefinition put(String path, Handler handler);

    RouteDefinition patch(String path, Handler handler);

    RouteDefinition delete(String path, Handler handler);

    /**
     * Starts a typed GET route: {@code routes.get("/users/{id}").to(this::find)}.
     */
    RouteBuilder get(String path);

    RouteBuilder post(String path);

    RouteBuilder put(String path);

    RouteBuilder patch(String path);

    RouteBuilder delete(String path);

    /**
     * Registers routes under a path prefix. {@code group("/users", users -> users.get("/", h))}
     * registers {@code /users}. Groups can be nested.
     */
    void group(String prefix, Consumer<Router> routes);

    /**
     * Applies a middleware to every route of this router or group, including nested groups. Must be
     * called before any route or group is declared here. Outer middleware runs first; within a
     * group, in declaration order.
     */
    void use(Middleware middleware);
}
