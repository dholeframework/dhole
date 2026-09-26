package org.dhole.internal.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.dhole.http.HttpStatus;
import org.dhole.http.Response;
import org.dhole.web.AppException;
import org.dhole.web.BindingException;
import org.dhole.web.ErrorHandler;
import org.dhole.web.ErrorResponse;

/**
 * Error handlers by exception type (ERRORS.md §12). The handler of the nearest registered class in
 * the exception's superclass chain wins, so specific handlers take precedence over general ones;
 * one handler per class. Exceptions without a handler are unexpected: the pipeline answers them with
 * {@code 500 INTERNAL_ERROR}.
 *
 * <p>Internal: there is no public registration API yet.
 */
final class ErrorHandlerRegistry {

    private final Map<Class<?>, ErrorHandler<Throwable>> handlers = new LinkedHashMap<>();

    /**
     * The built-in mapping: binding, validation, media type and application errors.
     */
    static ErrorHandlerRegistry defaults() {
        return new ErrorHandlerRegistry()
                .handle(BindingException.class, error -> error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", error.getMessage()))
                .handle(InvalidParameterException.class, error -> Response.badRequest(
                        ErrorResponse.of("INVALID_PARAMETER", error.getMessage()).details(error.details())))
                .handle(ValidationFailedException.class, error -> Response.status(HttpStatus.UNPROCESSABLE_CONTENT)
                        .body(ErrorResponse.validation(error.result())))
                .handle(UnsupportedMediaTypeException.class, error -> error(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "UNSUPPORTED_MEDIA_TYPE", "The request Content-Type is not supported."))
                .handle(NotAcceptableException.class, error -> error(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE",
                        "None of the accepted media types can be produced."))
                .handle(AppException.class, error -> error(error.status(), error.code(), error.getMessage()));
    }

    /**
     * @throws IllegalStateException if the type already has a handler
     */
    @SuppressWarnings("unchecked")
    <E extends Throwable> ErrorHandlerRegistry handle(Class<E> type, ErrorHandler<? super E> handler) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(handler, "handler");
        if (handlers.putIfAbsent(type, (ErrorHandler<Throwable>) handler) != null) {
            throw new IllegalStateException("An error handler for " + type.getName() + " is already registered");
        }
        return this;
    }

    Optional<ErrorHandler<Throwable>> find(Throwable error) {
        for (Class<?> type = error.getClass(); type != null; type = type.getSuperclass()) {
            ErrorHandler<Throwable> handler = handlers.get(type);
            if (handler != null) {
                return Optional.of(handler);
            }
        }
        return Optional.empty();
    }

    static Response error(HttpStatus status, String code, String message) {
        return Response.status(status).body(ErrorResponse.of(code, message));
    }
}
