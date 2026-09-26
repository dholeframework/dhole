package org.dhole.routing;

import org.dhole.http.Request;

/**
 * Executes a request already matched by the router.
 *
 * <pre>{@code
 * routes.get("/hello", request -> "Hello World");
 * }</pre>
 *
 * The result is a {@link org.dhole.http.Response}, a {@code String} (plain text) or {@code null}
 * (no content). Binding Java method parameters to the request is not part of this contract.
 */
@FunctionalInterface
public interface Handler {

    Object handle(Request request) throws Exception;
}
