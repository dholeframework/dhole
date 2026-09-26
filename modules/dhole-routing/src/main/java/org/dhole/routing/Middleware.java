package org.dhole.routing;

import org.dhole.http.Request;
import org.dhole.http.Response;

/**
 * Runs around the handler of the routes it applies to. A middleware may return without calling
 * {@code next}, which ends the pipeline early.
 *
 * <pre>{@code
 * routes.group("/admin", admin -> {
 *     admin.use((request, next) -> {
 *         // before
 *         Response response = next.handle(request);
 *         // after
 *         return response;
 *     });
 *     admin.get("/users", this::listUsers);
 * });
 * }</pre>
 */
@FunctionalInterface
public interface Middleware {

    Response handle(Request request, Next next) throws Exception;
}
