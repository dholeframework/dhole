package org.dhole.internal.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.dhole.http.HttpHandler;
import org.dhole.http.HttpServer;
import org.dhole.http.Request;
import org.dhole.http.Response;
import org.dhole.internal.di.ComponentMetadata;
import org.dhole.internal.di.ComponentScope;
import org.dhole.internal.di.ContainerBuilder;
import org.dhole.internal.http.JdkHttpServer;
import org.dhole.routing.Router;
import org.dhole.routing.RoutingException;
import org.dhole.web.Body;
import org.dhole.web.Controller;
import org.dhole.web.Header;
import org.dhole.web.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Typed routes over real HTTP, with component and route indexes written in their documented formats.
 */
class TypedRoutesTest {

    private static final String PREFIX = "org.dhole.internal.web.TypedRoutesTest$";
    private static final String CONTROLLER = PREFIX + "UserController";

    private static final String ROUTES = """
            dhole-routes 1

            route %1$s DELETE /users/{id}
            handler remove
            parameter id PATH long
            parameter request REQUEST org.dhole.http.Request
            response org.dhole.http.Response

            route %1$s GET /users
            handler list
            parameter term QUERY org.dhole.web.Query<java.lang.String>
            parameter pageSize HEADER org.dhole.web.Header<java.lang.Integer>
            response java.util.List<%2$sUser>

            route %1$s GET /users/{id}
            handler find
            parameter id PATH long
            response %2$sUser

            route %1$s GET /users/{id}/name
            handler name
            parameter id PATH long
            response java.lang.String

            route %1$s POST /users
            handler create
            parameter input BODY %2$sCreateUser
            response %2$sUser

            route %1$s PUT /users/{id}
            handler replace
            parameter id PATH long
            parameter input BODY org.dhole.web.Body<%2$sCreateUser>
            response %2$sUser
            """;

    @TempDir
    Path indexDirectory;

    private final HttpClient client = HttpClient.newHttpClient();
    private final Log log = new Log();
    private WebRuntime runtime;
    private URLClassLoader loader;

    @AfterEach
    void stop() throws IOException {
        if (runtime != null) {
            runtime.close();
        }
        if (loader != null) {
            loader.close();
        }
        client.close();
    }

    // Binding and JSON

    @Test
    void pathParameterIsConvertedAndTheResultIsJson() throws Exception {
        start(ROUTES);

        HttpResponse<String> response = send("GET", "/users/42", null, null, null);

        assertEquals(200, response.statusCode());
        assertEquals("{\"id\":42,\"name\":\"user 42\"}", response.body());
        assertEquals(Optional.of("application/json"), response.headers().firstValue("Content-Type"));
    }

    @Test
    void invalidPathValueIsABadRequestWithoutCallingTheHandler() throws Exception {
        start(ROUTES);

        HttpResponse<String> response = send("GET", "/users/abc", null, null, null);

        assertEquals("{\"error\":{\"code\":\"INVALID_PARAMETER\",\"message\":\"Invalid path parameter 'id': expected long.\","
                + "\"details\":{\"parameter\":\"id\",\"source\":\"path\",\"expected\":\"long\"},\"requestId\":\"*\"}}",
                ErrorBodies.envelope(response, 400));
        assertEquals(List.of(), log.events());
    }

    @Test
    void jsonBodyIsBoundToARecord() throws Exception {
        start(ROUTES);

        HttpResponse<String> response = send("POST", "/users", "{\"name\":\"Mamadu\"}", "application/json", null);

        assertEquals(200, response.statusCode());
        assertEquals("{\"id\":1,\"name\":\"Mamadu\"}", response.body());
    }

    @Test
    void explicitBodyWrapperAndPathAreBoundTogether() throws Exception {
        start(ROUTES);

        HttpResponse<String> response = send("PUT", "/users/9", "{\"name\":\"New\"}", "application/json; charset=UTF-8", null);

        assertEquals("{\"id\":9,\"name\":\"New\"}", response.body());
    }

    @Test
    void invalidJsonBodiesAreBadRequests() throws Exception {
        start(ROUTES);

        HttpResponse<String> malformed = send("POST", "/users", "{\"name\":", "application/json", null);
        HttpResponse<String> unknown = send("POST", "/users", "{\"name\":\"a\",\"admin\":true}", "application/json", null);
        HttpResponse<String> empty = send("POST", "/users", "", "application/json", null);

        assertEquals(ErrorBodies.error("BAD_REQUEST", "Invalid request body: Malformed JSON"),
                ErrorBodies.envelope(malformed, 400));
        assertEquals(ErrorBodies.error("BAD_REQUEST", "Invalid request body: Unknown JSON property '$.admin'"),
                ErrorBodies.envelope(unknown, 400));
        assertEquals(ErrorBodies.error("BAD_REQUEST", "Missing request body."), ErrorBodies.envelope(empty, 400));
        assertFalse(malformed.body().contains("jackson") || malformed.body().contains("Jackson"), malformed.body());
    }

    @Test
    void unsupportedOrMissingContentTypeIs415() throws Exception {
        start(ROUTES);

        assertEquals(415, send("POST", "/users", "name=a", "application/x-www-form-urlencoded", null).statusCode());
        assertEquals(415, send("POST", "/users", "{\"name\":\"a\"}", null, null).statusCode());
        assertEquals(415, send("POST", "/users", "{\"name\":\"a\"}", "not a media type", null).statusCode());
    }

    @Test
    void acceptNegotiationProducesJsonOr406BeforeTheHandlerRuns() throws Exception {
        start(ROUTES);

        assertEquals(200, send("GET", "/users/1", null, null, "application/json").statusCode());
        assertEquals(200, send("GET", "/users/1", null, null, "text/html, application/*;q=0.5").statusCode());
        List<String> before = log.events();
        HttpResponse<String> rejected = send("GET", "/users/1", null, null, "text/html");
        assertEquals(ErrorBodies.error("NOT_ACCEPTABLE", "None of the accepted media types can be produced."),
                ErrorBodies.envelope(rejected, 406));
        assertEquals(before, log.events());
        assertEquals("user 3", send("GET", "/users/3/name", null, null, "text/plain").body());
        assertEquals(406, send("GET", "/users/3/name", null, null, "application/json").statusCode());
    }

    @Test
    void queryAndHeaderWrappersAreBoundAndListsAreJson() throws Exception {
        start(ROUTES);

        HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri("/users?term=ma"))
                .header("Page-Size", "2").build(), BodyHandlers.ofString());

        assertEquals("[{\"id\":1,\"name\":\"ma 1\"},{\"id\":2,\"name\":\"ma 2\"}]", response.body());
        assertEquals(400, send("GET", "/users", null, null, null).statusCode());
        HttpResponse<String> badHeader = client.send(HttpRequest.newBuilder(uri("/users?term=ma"))
                .header("Page-Size", "two").build(), BodyHandlers.ofString());
        assertTrue(ErrorBodies.envelope(badHeader, 400).contains("\"code\":\"INVALID_PARAMETER\",\"message\":\"Invalid header "
                + "parameter 'pageSize': expected Integer.\",\"details\":{\"parameter\":\"pageSize\",\"source\":\"header\""),
                badHeader.body());
    }

    @Test
    void requestParametersAndRequestScopeWorkForTypedRoutes() throws Exception {
        start(ROUTES);

        HttpResponse<String> response = send("DELETE", "/users/5", null, null, null);

        assertEquals(204, response.statusCode());
        assertEquals(List.of("open probe", "remove 5", "close probe"), log.events());
    }

    // Startup validation before readiness

    @Test
    void typedRouteWithoutMetadataFailsBeforeListening() {
        RecordingServer server = new RecordingServer();

        RoutingException failure = assertThrows(RoutingException.class,
                () -> WebRuntime.start(components(), routes("dhole-routes 1\n"), builder(), server));

        assertTrue(failure.getMessage().startsWith("Metadata Error\n\nTyped route GET /users of UserController "
                + "cannot be bound: it has no build-time metadata"), failure.getMessage());
        assertTrue(failure.getMessage().endsWith("Rebuild the application."), failure.getMessage());
        assertFalse(server.started);
    }

    @Test
    void staleMetadataFailsBeforeListening() {
        String staleArity = ROUTES.replace("parameter request REQUEST org.dhole.http.Request\n", "");
        String unknownType = ROUTES.replace("response %2$sUser\n\nroute %1$s GET /users/{id}/name",
                "response com.example.Gone\n\nroute %1$s GET /users/{id}/name");
        String wrongIdentity = ROUTES.replace("GET /users/{id}/name", "GET /users/{userId}/name");

        for (String index : List.of(staleArity, unknownType, wrongIdentity)) {
            RecordingServer server = new RecordingServer();
            RoutingException failure = assertThrows(RoutingException.class,
                    () -> WebRuntime.start(components(), routes(index), builder(), server));
            assertTrue(failure.getMessage().endsWith("Rebuild the application."), failure.getMessage());
            assertFalse(server.started);
        }
    }

    @Test
    void incompatibleRouteMetadataFailsBeforeListening() throws IOException {
        Path file = indexDirectory.resolve(RouteMetadata.LOCATION);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "dhole-routes 9\n", StandardCharsets.UTF_8);

        try (URLClassLoader routesLoader = new URLClassLoader(new URL[] {indexDirectory.toUri().toURL()}, null)) {
            RoutingException failure = assertThrows(RoutingException.class, () -> RouteMetadata.load(routesLoader));
            assertTrue(failure.getMessage().startsWith("Metadata Compatibility Error"), failure.getMessage());
        }
    }

    // Fixtures

    public record User(long id, String name) {
    }

    public record CreateUser(String name) {
    }

    public static final class Log {

        private final List<String> events = Collections.synchronizedList(new ArrayList<>());

        void record(String event) {
            events.add(event);
        }

        List<String> events() {
            synchronized (events) {
                return List.copyOf(events);
            }
        }
    }

    public static final class Probe implements AutoCloseable {

        private final Log log;

        public Probe(Log log) {
            this.log = log;
            log.record("open probe");
        }

        @Override
        public void close() {
            log.record("close probe");
        }
    }

    public static final class UserController extends Controller {

        private final Log log;

        public UserController(Log log) {
            this.log = log;
        }

        @Override
        public void routes(Router routes) {
            routes.get("/users/{id}").to(this::find);
            routes.get("/users/{id}/name").to(this::name);
            routes.get("/users").to(this::list);
            routes.post("/users").to(this::create);
            routes.put("/users/{id}").to(this::replace);
            routes.delete("/users/{id}").to(this::remove);
        }

        User find(long id) {
            log.record("find " + id);
            return new User(id, "user " + id);
        }

        String name(long id) {
            return "user " + id;
        }

        List<User> list(Query<String> term, Header<Integer> pageSize) {
            List<User> users = new ArrayList<>();
            for (int index = 1; index <= pageSize.optional().orElse(1); index++) {
                users.add(new User(index, term.value() + " " + index));
            }
            return users;
        }

        User create(CreateUser input) {
            return new User(1, input.name());
        }

        User replace(long id, Body<CreateUser> input) {
            return new User(id, input.value().name());
        }

        Response remove(long id, Request request) {
            ((RoutedRequest) request).scope().resolve(Probe.class);
            log.record("remove " + id);
            return Response.noContent();
        }
    }

    private static final class RecordingServer implements HttpServer {

        private boolean started;

        @Override
        public void start(HttpHandler handler) {
            started = true;
        }

        @Override
        public void stop() {
        }
    }

    // Helpers

    private void start(String routes) {
        runtime = WebRuntime.start(components(), routes(routes), builder(), new JdkHttpServer("127.0.0.1", 0));
    }

    private ContainerBuilder builder() {
        return ContainerBuilder.create()
                .bind(Log.class).toInstance(log)
                .component(Probe.class, ComponentScope.REQUEST);
    }

    private static RouteMetadata routes(String text) {
        return RouteMetadata.parse(String.format(text, CONTROLLER, PREFIX));
    }

    private ComponentMetadata components() {
        String index = "dhole-metadata 1\n\ncomponent " + CONTROLLER + "\nconstructor " + PREFIX + "Log\n"
                + "supertype org.dhole.web.Controller\n";
        try {
            Path file = indexDirectory.resolve("META-INF/dhole/components.idx");
            Files.createDirectories(file.getParent());
            Files.writeString(file, index, StandardCharsets.UTF_8);
            if (loader == null) {
                loader = new URLClassLoader(new URL[] {indexDirectory.toUri().toURL()}, getClass().getClassLoader());
            }
            return ComponentMetadata.load(loader);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private HttpResponse<String> send(String method, String path, String body, String contentType, String accept)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path))
                .method(method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body));
        if (contentType != null) {
            request.header("Content-Type", contentType);
        }
        if (accept != null) {
            request.header("Accept", accept);
        }
        return client.send(request.build(), BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + runtime.port() + path);
    }
}
