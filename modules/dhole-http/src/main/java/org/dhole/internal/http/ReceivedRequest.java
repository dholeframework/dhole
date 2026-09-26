package org.dhole.internal.http;

import org.dhole.http.Headers;
import org.dhole.http.HttpMethod;
import org.dhole.http.Request;

/**
 * A request as received by a server adapter, before routing: it has no path parameters.
 */
record ReceivedRequest(HttpMethod method, String path, Headers headers, byte[] content, String ip)
        implements Request {

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
