package org.dhole.routing;

/**
 * Invalid route registration (malformed path, duplicate route, misplaced middleware, typed route
 * without handler) or invalid build-time route metadata. Raised at startup, before serving.
 */
public class RoutingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RoutingException(String message) {
        super(message);
    }
}
