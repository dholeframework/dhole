package org.dhole.internal.routing;

import java.util.List;
import java.util.Map;

import org.dhole.http.HttpMethod;

/**
 * Result of matching a request: a route with its raw path parameters, no route for the path
 * (404), or routes for the path but not for the method (405).
 */
public sealed interface RouteMatch {

    record Found(Route route, Map<String, String> parameters) implements RouteMatch {
    }

    record NotFound() implements RouteMatch {
    }

    /**
     * @param allowed the methods registered for the path, in {@link HttpMethod} declaration order
     */
    record MethodNotAllowed(List<HttpMethod> allowed) implements RouteMatch {
    }
}
