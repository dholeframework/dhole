package org.dhole.routing;

import org.dhole.http.Request;
import org.dhole.http.Response;

/**
 * The rest of the request pipeline after a {@link Middleware}: the next middleware or the handler.
 */
@FunctionalInterface
public interface Next {

    Response handle(Request request) throws Exception;
}
