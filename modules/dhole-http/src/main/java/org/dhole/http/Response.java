package org.dhole.http;

import java.util.Objects;

/**
 * An immutable HTTP response: status, headers and an optional body.
 *
 * <p>The body is an object; turning it into bytes is the job of response mapping (and, from M6,
 * serialization). Servers write {@code String} bodies as UTF-8 and {@code byte[]} bodies as is.
 */
public final class Response {

    private final HttpStatus status;
    private final Headers headers;
    private final Object body;

    private Response(HttpStatus status, Headers headers, Object body) {
        this.status = Objects.requireNonNull(status, "status");
        this.headers = Objects.requireNonNull(headers, "headers");
        this.body = body;
    }

    public static Response status(HttpStatus status) {
        return new Response(status, Headers.empty(), null);
    }

    public static Response ok() {
        return status(HttpStatus.OK);
    }

    public static Response ok(Object body) {
        return ok().body(body);
    }

    public static Response created(Object body) {
        return status(HttpStatus.CREATED).body(body);
    }

    public static Response noContent() {
        return status(HttpStatus.NO_CONTENT);
    }

    public static Response badRequest(Object body) {
        return status(HttpStatus.BAD_REQUEST).body(body);
    }

    public static Response unauthorized(Object body) {
        return status(HttpStatus.UNAUTHORIZED).body(body);
    }

    public static Response forbidden(Object body) {
        return status(HttpStatus.FORBIDDEN).body(body);
    }

    public static Response notFound(Object body) {
        return status(HttpStatus.NOT_FOUND).body(body);
    }

    public static Response conflict(Object body) {
        return status(HttpStatus.CONFLICT).body(body);
    }

    /**
     * Returns a copy with one more header value.
     */
    public Response header(String name, String value) {
        return new Response(status, headers.with(name, value), body);
    }

    /**
     * Returns a copy with the given body ({@code null} for none).
     */
    public Response body(Object body) {
        return new Response(status, headers, body);
    }

    public HttpStatus status() {
        return status;
    }

    public Headers headers() {
        return headers;
    }

    /**
     * Returns the body, or {@code null} when there is none.
     */
    public Object body() {
        return body;
    }

    @Override
    public String toString() {
        return "Response[" + status.code() + " " + status.reason() + "]";
    }
}
