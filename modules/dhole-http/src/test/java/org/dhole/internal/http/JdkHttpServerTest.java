package org.dhole.internal.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.dhole.http.HttpHandler;
import org.dhole.http.HttpMethod;
import org.dhole.http.HttpStatus;
import org.dhole.http.Request;
import org.dhole.http.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class JdkHttpServerTest {

    private final HttpClient client = HttpClient.newHttpClient();
    private final JdkHttpServer server = new JdkHttpServer("127.0.0.1", 0);

    @AfterEach
    void stopServer() {
        server.stop();
        client.close();
    }

    @Test
    void translatesRequestsAndResponses() throws Exception {
        AtomicReference<Request> received = new AtomicReference<>();
        server.start(request -> {
            received.set(request);
            return Response.created(new String(request.body(), StandardCharsets.UTF_8) + "!")
                    .header("X-Echo", request.header("X-Input"));
        });

        HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri("/users/a%20b"))
                .header("X-Input", "value")
                .POST(BodyPublishers.ofString("hello"))
                .build(), BodyHandlers.ofString());

        assertEquals(201, response.statusCode());
        assertEquals("hello!", response.body());
        assertEquals(Optional.of("value"), response.headers().firstValue("X-Echo"));
        assertEquals(Optional.of("text/plain; charset=UTF-8"), response.headers().firstValue("Content-Type"));
        assertEquals("/users/a%20b", received.get().path());
        assertEquals("127.0.0.1", received.get().ip());
        assertEquals(null, received.get().pathParameter("id"));
    }

    @Test
    void everyStandardMethodReachesTheHandler() throws Exception {
        server.start(request -> Response.ok(request.method().name()));

        for (String method : new String[] {"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"}) {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri("/"))
                    .method(method, BodyPublishers.noBody()).build(), BodyHandlers.ofString());
            assertEquals(method, response.body());
        }
    }

    @Test
    void headSendsStatusAndHeadersWithoutBodyBytes() throws Exception {
        AtomicReference<Request> received = new AtomicReference<>();
        server.start(request -> {
            received.set(request);
            return Response.ok("hello").header("X-Trace", "1");
        });

        HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder(uri("/"))
                .method("HEAD", BodyPublishers.noBody()).build(), BodyHandlers.ofByteArray());

        assertEquals(HttpMethod.HEAD, received.get().method());
        assertEquals(200, response.statusCode());
        assertEquals(0, response.body().length);
        assertEquals(Optional.of("5"), response.headers().firstValue("Content-Length"));
        assertEquals(Optional.of("text/plain; charset=UTF-8"), response.headers().firstValue("Content-Type"));
        assertEquals(Optional.of("1"), response.headers().firstValue("X-Trace"));
    }

    @Test
    void emptyBodiesAndNoContentAreWrittenWithoutBody() throws Exception {
        server.start(request -> Response.noContent());

        HttpResponse<String> response = get("/");

        assertEquals(204, response.statusCode());
        assertEquals("", response.body());
    }

    @Test
    void byteBodiesAreWrittenAsIs() throws Exception {
        server.start(request -> Response.ok(new byte[] {1, 2, 3}).header("Content-Type", "application/octet-stream"));

        HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder(uri("/")).build(), BodyHandlers.ofByteArray());

        assertEquals(3, response.body().length);
        assertEquals(Optional.of("application/octet-stream"), response.headers().firstValue("Content-Type"));
    }

    @Test
    void handlerFailureIsAnInternalServerError() throws Exception {
        server.start(request -> {
            throw new IllegalStateException("secret internal detail");
        });

        HttpResponse<String> response = get("/");

        assertEquals(500, response.statusCode());
        assertEquals("Internal Server Error", response.body());
    }

    @Test
    void unmappedBodyTypeIsAnInternalServerError() throws Exception {
        server.start(request -> Response.ok(new Object()));

        assertEquals(500, get("/").statusCode());
    }

    @Test
    void unknownMethodIsNotImplementedWithoutCallingTheHandler() throws Exception {
        AtomicReference<Request> received = new AtomicReference<>();
        server.start(request -> {
            received.set(request);
            return Response.ok();
        });

        HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri("/"))
                .method("PROPFIND", BodyPublishers.noBody()).build(), BodyHandlers.ofString());

        assertEquals(HttpStatus.NOT_IMPLEMENTED.code(), response.statusCode());
        assertEquals(null, received.get());
    }

    @Test
    void oversizedBodyIsRejected() throws Exception {
        server.start(request -> Response.ok());

        HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri("/"))
                .POST(BodyPublishers.ofByteArray(new byte[JdkHttpServer.MAX_BODY_BYTES + 1])).build(),
                BodyHandlers.ofString());

        assertEquals(413, response.statusCode());
    }

    @Test
    void stopReleasesThePortAndStartTwiceFails() throws Exception {
        HttpHandler handler = request -> Response.ok();
        server.start(handler);
        URI address = uri("/");
        assertThrows(IllegalStateException.class, () -> server.start(handler));

        server.stop();
        server.stop();

        IOException failure = assertThrows(IOException.class,
                () -> client.send(HttpRequest.newBuilder(address).build(), BodyHandlers.ofString()));
        assertTrue(failure instanceof ConnectException || failure.getCause() instanceof ConnectException, failure.toString());
        assertThrows(IllegalStateException.class, server::port);
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(uri(path)).build(), BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.port() + path);
    }
}
