package org.dhole.web;

import org.dhole.routing.Router;

/**
 * Base class of controllers: the structural components that declare routes.
 *
 * <pre>{@code
 * public final class HelloController extends Controller {
 *
 *     @Override
 *     public void routes(Router routes) {
 *         routes.get("/hello", request -> "Hello World");
 *     }
 * }
 * }</pre>
 *
 * Controllers are found at build time (no annotations), created through dependency injection with
 * their constructor dependencies, and {@link #routes(Router)} is called once at startup. Only the
 * routes declared there are exposed; other methods never become endpoints by themselves.
 */
public abstract class Controller {

    /**
     * Declares this controller's routes. Registration only: no business logic runs here.
     */
    public abstract void routes(Router routes);
}
