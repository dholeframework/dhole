package org.dhole.routing;

/**
 * Completes a typed route with the method that handles it; its parameters are bound from the
 * request (path parameters by name, explicit wrappers, a single structured body).
 *
 * <pre>{@code
 * routes.get("/users/{id}").to(this::find);
 * routes.post("/users").to(this::create);
 * }</pre>
 *
 * Typed routes need a path known at build time and a method reference of the controller
 * ({@code this::method}). A builder left without {@code to(...)} fails at startup.
 */
public interface RouteBuilder {

    RouteDefinition to(Handler0<?> handler);

    <A> RouteDefinition to(Handler1<A, ?> handler);

    <A, B> RouteDefinition to(Handler2<A, B, ?> handler);

    <A, B, C> RouteDefinition to(Handler3<A, B, C, ?> handler);
}
