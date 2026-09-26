package org.dhole.internal.web;

import java.util.Map;

import org.dhole.http.Headers;
import org.dhole.http.HttpMethod;
import org.dhole.http.Request;
import org.dhole.internal.di.RequestScope;

/**
 * A request matched by a route: the received request, its raw path parameters and its request scope.
 */
final class RoutedRequest implements Request {

    private final Request received;
    private final Map<String, String> parameters;
    private final RequestScope scope;

    RoutedRequest(Request received, Map<String, String> parameters, RequestScope scope) {
        this.received = received;
        this.parameters = Map.copyOf(parameters);
        this.scope = scope;
    }

    @Override
    public HttpMethod method() {
        return received.method();
    }

    @Override
    public String path() {
        return received.path();
    }

    @Override
    public String header(String name) {
        return received.header(name);
    }

    @Override
    public Headers headers() {
        return received.headers();
    }

    @Override
    public byte[] body() {
        return received.body();
    }

    @Override
    public String ip() {
        return received.ip();
    }

    @Override
    public String pathParameter(String name) {
        return parameters.get(name);
    }

    RequestScope scope() {
        return scope;
    }

    @Override
    public String toString() {
        return received.toString();
    }
}
