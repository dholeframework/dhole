package org.dhole.internal.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.dhole.http.Headers;
import org.dhole.http.HttpHandler;
import org.dhole.http.HttpMethod;
import org.dhole.http.HttpServer;
import org.dhole.http.HttpStatus;
import org.dhole.http.Request;
import org.dhole.http.Response;

import com.sun.net.httpserver.HttpExchange;

/**
 * The first official, internal {@link HttpServer} adapter, over the JDK's
 * {@code com.sun.net.httpserver} (HTTP/1.1). Each request runs on its own virtual thread; the
 * server owns that executor and releases it in {@link #stop()}. Not the permanent production
 * server architecture: other adapters can replace it behind the same SPI.
 *
 * <p>Public only so the internal web runtime can create it; not application API.
 */
public final class JdkHttpServer implements HttpServer {

    static final int MAX_BODY_BYTES = 1024 * 1024;
    private static final String TEXT = "text/plain; charset=UTF-8";

    private final String host;
    private final int port;
    private com.sun.net.httpserver.HttpServer server;
    private ExecutorService executor;

    /**
     * @param port the port to bind, {@code 0} for an ephemeral port
     */
    public JdkHttpServer(String host, int port) {
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
    }

    @Override
    public synchronized void start(HttpHandler handler) {
        Objects.requireNonNull(handler, "handler");
        if (server != null) {
            throw new IllegalStateException("The HTTP server is already started.");
        }
        com.sun.net.httpserver.HttpServer created;
        try {
            created = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(host, port), 0);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to bind the HTTP server to " + host + ":" + port, e);
        }
        executor = Executors.newVirtualThreadPerTaskExecutor();
        created.setExecutor(executor);
        created.createContext("/", exchange -> exchange(exchange, handler));
        created.start();
        server = created;
    }

    /**
     * Returns the bound port.
     *
     * @throws IllegalStateException if the server is not running
     */
    public synchronized int port() {
        if (server == null) {
            throw new IllegalStateException("The HTTP server is not running.");
        }
        return server.getAddress().getPort();
    }

    @Override
    public synchronized void stop() {
        if (server == null) {
            return;
        }
        server.stop(0);
        executor.close();
        server = null;
        executor = null;
    }

    private static void exchange(HttpExchange exchange, HttpHandler handler) {
        try (exchange) {
            write(exchange, respond(exchange, handler), "HEAD".equals(exchange.getRequestMethod()));
        } catch (IOException e) {
            // The client went away while the response was written; nothing is left to report to it.
        }
    }

    private static Response respond(HttpExchange exchange, HttpHandler handler) throws IOException {
        HttpMethod method;
        try {
            method = HttpMethod.valueOf(exchange.getRequestMethod());
        } catch (IllegalArgumentException e) {
            return text(HttpStatus.NOT_IMPLEMENTED);
        }
        byte[] body = readBody(exchange.getRequestBody());
        if (body == null) {
            return text(HttpStatus.CONTENT_TOO_LARGE);
        }
        String path = exchange.getRequestURI().getRawPath();
        Map<String, List<String>> query;
        try {
            query = parseQuery(exchange.getRequestURI().getRawQuery());
        } catch (IllegalArgumentException e) {
            return text(HttpStatus.BAD_REQUEST);
        }
        Request request = new ReceivedRequest(method, path == null || path.isEmpty() ? "/" : path, query,
                Headers.of(exchange.getRequestHeaders()), body,
                exchange.getRemoteAddress().getAddress().getHostAddress());
        try {
            return Objects.requireNonNull(handler.handle(request), "handler returned no response");
        } catch (Exception e) {
            return text(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Parses a raw query string, form-decoding names and values; repeated names keep their order.
     *
     * @throws IllegalArgumentException if the encoding is malformed
     */
    static Map<String, List<String>> parseQuery(String raw) {
        Map<String, List<String>> query = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return query;
        }
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int separator = pair.indexOf('=');
            String name = URLDecoder.decode(separator < 0 ? pair : pair.substring(0, separator), StandardCharsets.UTF_8);
            String value = separator < 0 ? "" : URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
            query.computeIfAbsent(name, key -> new ArrayList<>()).add(value);
        }
        query.replaceAll((name, values) -> List.copyOf(values));
        return query;
    }

    /**
     * Returns the body, or {@code null} when it is larger than {@link #MAX_BODY_BYTES}.
     */
    private static byte[] readBody(InputStream input) throws IOException {
        byte[] body = input.readNBytes(MAX_BODY_BYTES + 1);
        return body.length > MAX_BODY_BYTES ? null : body;
    }

    /**
     * Writes the response. For {@code HEAD}, sends the same status and headers, including the
     * {@code Content-Length} of the body, but no body bytes.
     */
    private static void write(HttpExchange exchange, Response response, boolean head) throws IOException {
        byte[] body;
        Response written = response;
        if (response.body() == null) {
            body = new byte[0];
        } else if (response.body() instanceof byte[] bytes) {
            body = bytes;
        } else if (response.body() instanceof String text) {
            body = text.getBytes(StandardCharsets.UTF_8);
            if (response.headers().first("Content-Type") == null) {
                written = response.header("Content-Type", TEXT);
            }
        } else {
            written = text(HttpStatus.INTERNAL_SERVER_ERROR);
            body = ((String) written.body()).getBytes(StandardCharsets.UTF_8);
        }
        for (String name : written.headers().names()) {
            exchange.getResponseHeaders().put(name, written.headers().all(name));
        }
        boolean empty = body.length == 0;
        if (head) {
            if (!empty) {
                exchange.getResponseHeaders().set("Content-Length", Integer.toString(body.length));
            }
            exchange.sendResponseHeaders(written.status().code(), -1);
            return;
        }
        exchange.sendResponseHeaders(written.status().code(), empty ? -1 : body.length);
        if (!empty) {
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        }
    }

    private static Response text(HttpStatus status) {
        return Response.status(status).body(status.reason()).header("Content-Type", TEXT);
    }
}
