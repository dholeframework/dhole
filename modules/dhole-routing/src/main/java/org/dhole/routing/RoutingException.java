package org.dhole.routing;

/**
 * Invalid route registration: malformed path, duplicate route or misplaced middleware.
 */
public class RoutingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RoutingException(String message) {
        super(message);
    }
}
