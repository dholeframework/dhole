package org.dhole.routing;

import org.dhole.http.HttpMethod;

/**
 * A registered route.
 */
public interface RouteDefinition {

    HttpMethod method();

    /**
     * Returns the full path template, group prefixes included, for example {@code /api/users/{id}}.
     */
    String path();
}
