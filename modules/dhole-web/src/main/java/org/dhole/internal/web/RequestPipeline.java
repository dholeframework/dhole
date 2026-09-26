package org.dhole.internal.web;

import java.util.List;
import java.util.Objects;

import org.dhole.http.HttpHandler;
import org.dhole.http.HttpStatus;
import org.dhole.http.Request;
import org.dhole.http.Response;
import org.dhole.internal.di.DependencyContainer;
import org.dhole.internal.di.RequestScope;
import org.dhole.internal.routing.RouteMatch;
import org.dhole.internal.routing.RouteMatcher;
import org.dhole.routing.Handler;
import org.dhole.routing.Middleware;

/**
 * The M5 request pipeline: route matching, request scope, middleware, handler and response mapping.
 *
 * <pre>
 * route match   -> 404 (no route for the path) / 405 (path known, method not)
 * request scope -> opened per request, always closed (success or failure)
 * middleware    -> outermost first; may end the pipeline early
 * handler       -> result mapped: Response as is, String as text/plain, null as 204
 * error boundary-> any exception becomes 500 without internal details
 * </pre>
 *
 * Serialization (M6), validation and error mapping (M7) and security (M10) are later stages.
 */
final class RequestPipeline implements HttpHandler {

    private static final String TEXT = "text/plain; charset=UTF-8";

    private final RouteMatcher routes;
    private final DependencyContainer container;

    RequestPipeline(RouteMatcher routes, DependencyContainer container) {
        this.routes = Objects.requireNonNull(routes, "routes");
        this.container = Objects.requireNonNull(container, "container");
    }

    @Override
    public Response handle(Request received) {
        RouteMatch match = routes.match(received.method(), received.path());
        if (match instanceof RouteMatch.NotFound) {
            return text(HttpStatus.NOT_FOUND);
        }
        if (match instanceof RouteMatch.MethodNotAllowed) {
            return text(HttpStatus.METHOD_NOT_ALLOWED);
        }
        RouteMatch.Found found = (RouteMatch.Found) match;
        RequestScope scope = container.openRequestScope();
        Response response;
        try {
            RoutedRequest request = new RoutedRequest(received, found.parameters(), scope);
            response = next(found.route().middleware(), 0, found.route().handler(), request);
        } catch (Exception e) {
            response = text(HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            try {
                scope.close();
            } catch (RuntimeException e) {
                response = text(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
        return response;
    }

    private static Response next(List<Middleware> middleware, int index, Handler handler, Request request)
            throws Exception {
        if (index < middleware.size()) {
            return Objects.requireNonNull(
                    middleware.get(index).handle(request, forwarded -> next(middleware, index + 1, handler, forwarded)),
                    "middleware returned no response");
        }
        return map(handler.handle(request));
    }

    /**
     * Maps a handler result to a response. Other types are rejected until serialization (M6).
     */
    static Response map(Object result) {
        if (result == null) {
            return Response.noContent();
        }
        if (result instanceof Response response) {
            return response;
        }
        if (result instanceof String text) {
            return Response.ok(text).header("Content-Type", TEXT);
        }
        throw new IllegalStateException("No response mapping for " + result.getClass().getName()
                + "; return a Response, a String or null");
    }

    private static Response text(HttpStatus status) {
        return Response.status(status).body(status.reason()).header("Content-Type", TEXT);
    }
}
