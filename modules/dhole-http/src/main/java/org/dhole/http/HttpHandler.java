package org.dhole.http;

/**
 * Handles every request received by an {@link HttpServer}. Implemented by the framework's request
 * pipeline, not by application code.
 */
@FunctionalInterface
public interface HttpHandler {

    Response handle(Request request) throws Exception;
}
