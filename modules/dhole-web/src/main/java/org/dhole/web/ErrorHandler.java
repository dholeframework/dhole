package org.dhole.web;

import org.dhole.http.Response;

/**
 * Turns an exception into a response. A response whose body is an {@link ErrorResponse} is written
 * as the error envelope with the request ID added.
 */
@FunctionalInterface
public interface ErrorHandler<E extends Throwable> {

    Response handle(E error);
}
