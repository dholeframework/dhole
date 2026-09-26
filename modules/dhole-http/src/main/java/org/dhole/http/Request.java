package org.dhole.http;

/**
 * An HTTP request.
 */
public interface Request {

    HttpMethod method();

    /**
     * Returns the request path as received, percent-encoded, without query string; {@code /} when
     * empty.
     */
    String path();

    /**
     * Returns the first value of a header (names are case-insensitive), or {@code null} when absent.
     */
    String header(String name);

    Headers headers();

    /**
     * Returns the request body; empty when there is none.
     */
    byte[] body();

    /**
     * Returns the client IP address.
     */
    String ip();

    /**
     * Returns the raw (percent-decoded, unconverted) value of a path parameter of the matched
     * route, for example {@code id} in {@code /users/{id}}, or {@code null} when absent.
     */
    String pathParameter(String name);
}
