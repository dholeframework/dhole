package org.dhole.internal.web;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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
import org.dhole.web.ErrorHandler;
import org.dhole.web.ErrorResponse;

/**
 * The request pipeline: request ID, route matching, request scope, middleware, binding, validation,
 * handler, response mapping and the error boundary.
 *
 * <pre>
 * request ID    -> a generated UUID; X-Request-Id on every response, requestId in every error
 * route match   -> 404 NOT_FOUND / 405 METHOD_NOT_ALLOWED with Allow; HEAD uses GET
 * request scope -> opened per request, always closed (success or failure)
 * middleware    -> outermost first; may end the pipeline early
 * typed route   -> 406 when Accept excludes the declared response; binding: 415 for an unsupported
 *                  body Content-Type, 400 for missing or unconvertible values and invalid bodies;
 *                  422 when a Validatable body fails its rules (the handler does not run)
 * handler       -> raw Handler(Request) or typed Handler0..3 with bound arguments
 * mapping       -> null: 204; Response: as is, object bodies serialized; String: text/plain;
 *                  other values: serialized with the negotiated serializer (JSON); 406 if none
 * error boundary-> the nearest registered error handler, otherwise 500 INTERNAL_ERROR; the stack
 *                  trace goes to the error log with the request ID, never to the client
 * </pre>
 *
 * Error bodies are the JSON error envelope. Security (M10) is a later stage.
 */
final class RequestPipeline implements HttpHandler {

    static final String REQUEST_ID = "X-Request-Id";
    private static final String TEXT = "text/plain; charset=UTF-8";

    private final RouteMatcher routes;
    private final DependencyContainer container;
    private final Map<Route, BindingPlan> plans;
    private final SerializerRegistry serializers;
    private final ErrorHandlerRegistry errors;
    private final RuntimeMode mode;
    private final PrintStream log;

    RequestPipeline(RouteMatcher routes, DependencyContainer container, Map<Route, BindingPlan> plans,
            SerializerRegistry serializers, ErrorHandlerRegistry errors, RuntimeMode mode, PrintStream log) {
        this.routes = Objects.requireNonNull(routes, "routes");
        this.container = Objects.requireNonNull(container, "container");
        this.plans = Map.copyOf(plans);
        this.serializers = Objects.requireNonNull(serializers, "serializers");
        this.errors = Objects.requireNonNull(errors, "errors");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public Response handle(Request received) {
        String requestId = UUID.randomUUID().toString();
        Response response;
        try {
            response = route(received);
        } catch (Exception e) {
            response = error(e, received, requestId);
        }
        try {
            return finish(response, requestId);
        } catch (RuntimeException e) {
            return finish(unexpected(e, received, requestId), requestId);
        }
    }

    private Response route(Request received) throws Exception {
        RouteMatch match = routes.match(received.method(), received.path());
        if (match instanceof RouteMatch.NotFound) {
            return ErrorHandlerRegistry.error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Route not found.");
        }
        if (match instanceof RouteMatch.MethodNotAllowed notAllowed) {
            return ErrorHandlerRegistry.error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Method not allowed.")
                    .header("Allow", String.join(", ", notAllowed.allowed().stream().map(Enum::name).toList()));
        }
        RouteMatch.Found found = (RouteMatch.Found) match;
        RequestScope scope = container.openRequestScope();
        Response response = null;
        Exception failure = null;
        try {
            RoutedRequest request = new RoutedRequest(received, found.parameters(), scope);
            response = next(found.route(), found.route().middleware(), 0, request);
        } catch (Exception e) {
            failure = e;
        }
        try {
            scope.close();
        } catch (RuntimeException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        if (failure != null) {
            throw failure;
        }
        return response;
    }

    /**
     * Maps an exception with its error handler, or to {@code 500}. A failing error handler is itself
     * an unexpected error.
     */
    private Response error(Exception failure, Request request, String requestId) {
        Optional<ErrorHandler<Throwable>> handler = errors.find(failure);
        if (handler.isEmpty()) {
            return unexpected(failure, request, requestId);
        }
        try {
            return Objects.requireNonNull(handler.get().handle(failure), "error handler returned no response");
        } catch (RuntimeException handlerFailure) {
            handlerFailure.addSuppressed(failure);
            return unexpected(handlerFailure, request, requestId);
        }
    }

    private Response unexpected(Exception failure, Request request, String requestId) {
        synchronized (log) {
            log.println("Unexpected error [requestId=" + requestId + "] " + request.method() + " " + request.path());
            failure.printStackTrace(log);
        }
        ErrorResponse body = ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred.");
        if (mode == RuntimeMode.DEVELOPMENT) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("exception", failure.getClass().getName());
            if (failure.getMessage() != null) {
                details.put("message", failure.getMessage());
            }
            body = body.details(details);
        }
        return Response.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /**
     * Writes an error envelope as JSON with the request ID and adds {@code X-Request-Id}.
     */
    private Response finish(Response response, String requestId) {
        if (response.body() instanceof ErrorResponse error) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            serializers.forMediaType(MediaType.APPLICATION_JSON).orElseThrow()
                    .serialize(error.requestId(requestId).envelope(), TypeRef.of(Map.class), output);
            response = response.body(output.toByteArray()).header("Content-Type", MediaType.APPLICATION_JSON.toString());
        }
        return response.header(REQUEST_ID, requestId);
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
            if (body == null || body instanceof String || body instanceof byte[] || body instanceof ErrorResponse) {
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
}
