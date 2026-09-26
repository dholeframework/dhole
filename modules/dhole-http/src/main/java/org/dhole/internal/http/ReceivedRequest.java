package org.dhole.internal.http;

import java.util.List;
import java.util.Map;

import org.dhole.http.Headers;
import org.dhole.http.HttpMethod;
import org.dhole.http.Request;

/**
 * A request as received by a server adapter, before routing: it has no path parameters.
 */
record ReceivedRequest(HttpMethod method, String path, Map<String, List<String>> query, Headers headers,
        byte[] content, String ip) implements Request {

    @Override
    public String queryParameter(String name) {
        List<String> values = query.get(name);
        return values == null ? null : values.get(0);
    }

    @Override
    public String header(String name) {
        return headers.first(name);
    }

    @Override
    public byte[] body() {
        return content.clone();
    }

    @Override
    public String pathParameter(String name) {
        return null;
    }

    @Override
    public String toString() {
        return "Request[" + method + " " + path + "]";
    }
}
