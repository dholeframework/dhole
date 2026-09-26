package org.dhole.internal.web;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.dhole.http.HttpHandler;
import org.dhole.http.HttpStatus;
import org.dhole.http.Request;
import org.dhole.http.Response;
import org.dhole.internal.di.DependencyContainer;
import org.dhole.internal.di.RequestScope;
import org.dhole.internal.routing.Route;
import org.dhole.internal.routing.RouteMatch;
import org.dhole.internal.routing.RouteMatcher;
import org.dhole.routing.Middleware;
import org.dhole.serialization.MediaType;
import org.dhole.serialization.SerializerRegistry;
import org.dhole.serialization.TypeRef;
import org.dhole.web.BindingException;

/**
 * The request pipeline: route matching, request scope, middleware, binding, handler and response
 * mapping.
 *
 * <pre>
 * route match   -> 404 / 405 with Allow; HEAD uses GET
 * request scope -> opened per request, always closed (success or failure)
 * middleware    -> outermost first; may end the pipeline early
 * typed route   -> 406 when Accept excludes the declared response; binding: 415 for an unsupported
 *                  body Content-Type, 400 for missing or unconvertible values and invalid bodies
 * handler       -> raw Handler(Request) or typed Handler0..3 with bound arguments
 * mapping       -> null: 204; Response: as is, object bodies serialized; String: text/plain;
 *                  other values: serialized with the negotiated serializer (JSON); 406 if none
 * error boundary-> any other exception becomes 500 without internal details
 * </pre>
 *
 * Validation and error mapping (M7) and security (M10) are later stages.
 */
final class RequestPipeline implements HttpHandler {

    private static final String TEXT = "text/plain; charset=UTF-8";

    private final RouteMatcher routes;
    private final DependencyContainer container;
    private final Map<Route, BindingPlan> plans;
    private final SerializerRegistry serializers;

    RequestPipeline(RouteMatcher routes, DependencyContainer container, Map<Route, BindingPlan> plans,
            SerializerRegistry serializers) {
        this.routes = Objects.requireNonNull(routes, "routes");
        this.container = Objects.requireNonNull(container, "container");
        this.plans = Map.copyOf(plans);
        this.serializers = Objects.requireNonNull(serializers, "serializers");
    }

    @Override
    public Response handle(Request received) {
        RouteMatch match = routes.match(received.method(), received.path());
        if (match instanceof RouteMatch.NotFound) {
            return text(HttpStatus.NOT_FOUND);
        }
        if (match instanceof RouteMatch.MethodNotAllowed notAllowed) {
            return text(HttpStatus.METHOD_NOT_ALLOWED).header("Allow",
                    String.join(", ", notAllowed.allowed().stream().map(Enum::name).toList()));
        }
        RouteMatch.Found found = (RouteMatch.Found) match;
        RequestScope scope = container.openRequestScope();
        Response response;
        try {
            RoutedRequest request = new RoutedRequest(received, found.parameters(), scope);
            response = next(found.route(), found.route().middleware(), 0, request);
        } catch (BindingException e) {
            response = Response.badRequest("Bad Request: " + e.getMessage()).header("Content-Type", TEXT);
        } catch (UnsupportedMediaTypeException e) {
            response = text(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        } catch (NotAcceptableException e) {
            response = text(HttpStatus.NOT_ACCEPTABLE);
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

    private Response next(Route route, List<Middleware> middleware, int index, Request request) throws Exception {
        if (index < middleware.size()) {
            return Objects.requireNonNull(
                    middleware.get(index).handle(request, forwarded -> next(route, middleware, index + 1, forwarded)),
                    "middleware returned no response");
        }
        String accept = request.header("Accept");
        if (route.typed().isEmpty()) {
            return map(route.handler().handle(request), Optional.empty(), accept);
        }
        BindingPlan plan = plans.get(route);
        requireAcceptable(plan.response(), accept);
        Object result = route.typed().get().invoke(plan.bind(routed(request)));
        return map(result, Optional.of(plan.response()), accept);
    }

    /**
     * Rejects a typed request before binding and invoking the handler when the client accepts none
     * of the media types the declared response could be written as.
     */
    private void requireAcceptable(TypeRef<?> response, String accept) {
        Class<?> raw = response.rawType();
        if (raw == Response.class || raw == void.class || raw == Void.class) {
            return;
        }
        List<MediaType> producible = raw == String.class ? List.of(MediaType.TEXT_PLAIN) : serializers.mediaTypes();
        if (MediaType.select(accept, producible).isEmpty()) {
            throw new NotAcceptableException();
        }
    }

    /**
     * Maps a handler result to a response.
     */
    private Response map(Object result, Optional<TypeRef<?>> declared, String accept) {
        if (result == null) {
            return Response.noContent();
        }
        if (result instanceof Response response) {
            Object body = response.body();
            if (body == null || body instanceof String || body instanceof byte[]) {
                return response;
            }
            return serialize(response, body, TypeRef.of(body.getClass()), accept);
        }
        if (result instanceof String text) {
            if (MediaType.select(accept, List.of(MediaType.TEXT_PLAIN)).isEmpty()) {
                throw new NotAcceptableException();
            }
            return Response.ok(text).header("Content-Type", TEXT);
        }
        TypeRef<?> type = declared.filter(ref -> ref.rawType().isInstance(result))
                .orElseGet(() -> TypeRef.of(result.getClass()));
        return serialize(Response.ok(), result, type, accept);
    }

    private Response serialize(Response response, Object body, TypeRef<?> type, String accept) {
        MediaType selected = MediaType.select(accept, serializers.mediaTypes()).orElseThrow(NotAcceptableException::new);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        serializers.forMediaType(selected).orElseThrow().serialize(body, type, output);
        Response written = response.body(output.toByteArray());
        return written.headers().first("Content-Type") == null
                ? written.header("Content-Type", selected.toString())
                : written;
    }

    private static RoutedRequest routed(Request request) {
        if (request instanceof RoutedRequest routed) {
            return routed;
        }
        throw new IllegalStateException("Middleware must pass the request it was given to next");
    }

    private static Response text(HttpStatus status) {
        return Response.status(status).body(status.reason()).header("Content-Type", TEXT);
    }

    private static final class NotAcceptableException extends RuntimeException {

        private static final long serialVersionUID = 1L;
    }
}
