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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.dhole.di.AmbiguousDependencyException;
import org.dhole.di.CircularDependencyException;
import org.dhole.di.DependencyException;
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
import org.dhole.web.Controller;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebRuntimeTest {

    private static final String PREFIX = "org.dhole.internal.web.WebRuntimeTest$";

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

    // Controllers and routing through real HTTP

    @Test
    void controllerRootsServeEveryMethod() throws Exception {
        start(controller("ItemsController"));

        for (String method : List.of("GET", "POST", "PUT", "PATCH", "DELETE")) {
            HttpResponse<String> response = send(method, "/items");
            assertEquals(200, response.statusCode(), method);
            assertEquals(method + " items", response.body());
            assertEquals(Optional.of("text/plain; charset=UTF-8"), response.headers().firstValue("Content-Type"));
        }
    }

    @Test
    void unknownPathIs404AndUnregisteredMethodIs405() throws Exception {
        start(controller("HelloController"));

        assertEquals(404, send("GET", "/missing").statusCode());
        assertEquals("Not Found", send("GET", "/missing").body());
        HttpResponse<String> notAllowed = send("POST", "/hello");
        assertEquals(405, notAllowed.statusCode());
        assertEquals("Method Not Allowed", notAllowed.body());
    }

    @Test
    void rawRouteParametersReachTheHandler() throws Exception {
        start(controller("ItemsController"));

        assertEquals("item a b", send("GET", "/items/a%20b").body());
    }

    @Test
    void groupsComposePaths() throws Exception {
        start(controller("ApiController"));

        assertEquals("healthy", send("GET", "/api/health").body());
        assertEquals("users", send("GET", "/api/users").body());
        assertEquals(404, send("GET", "/health").statusCode());
    }

    @Test
    void handlerResultsAreMapped() throws Exception {
        start(controller("ResultsController"));

        HttpResponse<String> created = send("POST", "/created");
        assertEquals(201, created.statusCode());
        assertEquals(Optional.of("7"), created.headers().firstValue("X-Resource"));
        assertEquals(204, send("GET", "/nothing").statusCode());
        assertEquals(500, send("GET", "/unmapped").statusCode());
        HttpResponse<String> failure = send("GET", "/failure");
        assertEquals(500, failure.statusCode());
        assertEquals("Internal Server Error", failure.body());
    }

    // Middleware

    @Test
    void middlewareRunsInDeclarationOrderAroundTheHandler() throws Exception {
        start(controller("MiddlewareController"));

        assertEquals("handler", send("GET", "/admin/users").body());
        assertEquals(List.of("A before", "B before", "handler", "B after", "A after"), log.events());
    }

    @Test
    void middlewareCanEndThePipelineEarly() throws Exception {
        start(controller("MiddlewareController"));

        HttpResponse<String> response = send("GET", "/guarded/secret");

        assertEquals(401, response.statusCode());
        assertEquals("denied", response.body());
        assertFalse(log.events().contains("secret"), log.events().toString());
    }

    // Request scope

    @Test
    void requestScopeIsClosedAfterEachRequest() throws Exception {
        start(controller("ScopeController"));

        send("GET", "/scope");
        send("GET", "/scope");

        assertEquals(List.of("open 1", "close 1", "open 2", "close 2"), log.events());
    }

    @Test
    void requestScopeIsClosedWhenTheHandlerFails() throws Exception {
        start(controller("ScopeController"));

        assertEquals(500, send("GET", "/scope/failure").statusCode());

        assertEquals(List.of("open 1", "close 1"), log.events());
    }

    @Test
    void requestScopeIsClosedWhenMiddlewareFails() throws Exception {
        start(controller("ScopeController"));

        assertEquals(500, send("GET", "/scope/middleware").statusCode());

        assertEquals(List.of("open 1", "close 1"), log.events());
    }

    @Test
    void concurrentRequestsHaveIsolatedScopes() throws Exception {
        start(controller("ScopeController"));
        int requests = 12;
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int index = 0; index < requests; index++) {
                Callable<String> task = () -> {
                    go.await();
                    return send("GET", "/scope").body();
                };
                results.add(executor.submit(task));
            }
            go.countDown();
            List<String> probes = new ArrayList<>();
            for (Future<String> result : results) {
                probes.add(result.get());
            }
            assertEquals(requests, probes.stream().distinct().count(), probes.toString());
        } finally {
            executor.shutdownNow();
        }
        assertEquals(requests, log.events().stream().filter(event -> event.startsWith("close")).count());
    }

    // Startup validation before listening

    @Test
    void routeConflictFailsBeforeTheServerStartsAndReleasesResources() {
        RecordingServer server = new RecordingServer();

        RoutingException failure = assertThrows(RoutingException.class, () -> WebRuntime.start(
                metadata(controller("HelloController"), controller("OtherHelloController"), component("Resource")),
                components().component(Resource.class), server));

        assertEquals("Routing Error\n\nDuplicate route:\nGET /hello\n\nDeclared by:\nHelloController\n"
                + "OtherHelloController", failure.getMessage());
        assertFalse(server.started);
        assertEquals(List.of("close Resource"), log.events());
    }

    @Test
    void missingControllerDependencyFailsBeforeListening() {
        RecordingServer server = new RecordingServer();

        DependencyException failure = assertThrows(DependencyException.class, () -> WebRuntime.start(
                metadata(controller("NeedsGatewayController")), components(), server));

        assertTrue(failure.getMessage().startsWith("Dependency Error DHOLE-DI-001"), failure.getMessage());
        assertFalse(server.started);
    }

    @Test
    void ambiguousControllerDependencyFailsBeforeListening() {
        RecordingServer server = new RecordingServer();

        assertThrows(AmbiguousDependencyException.class, () -> WebRuntime.start(metadata(
                controller("NeedsGatewayController"), provider("StripeGateway"), provider("PaypalGateway")),
                components(), server));
        assertFalse(server.started);
    }

    @Test
    void circularControllerDependencyFailsBeforeListening() {
        RecordingServer server = new RecordingServer();

        CircularDependencyException failure = assertThrows(CircularDependencyException.class,
                () -> WebRuntime.start(metadata(controller("CycleController")), components(), server));

        assertTrue(failure.getMessage().startsWith("Circular Dependency DHOLE-DI-003"), failure.getMessage());
        assertFalse(server.started);
    }

    @Test
    void onlyControllersAndTheirReachableDependenciesAreActivated() throws Exception {
        start(controller("HelloController"), component("Resource"), """
                component %sBrokenUnrelated
                unusable multiple-public-constructors 2
                """);

        assertEquals("Hello World", send("GET", "/hello").body());
        assertEquals(List.of(), log.events());
        assertEquals(1, runtime.routes().routes().size());
    }

    @Test
    void closingTheRuntimeStopsTheServerAndClosesComponents() throws Exception {
        start(controller("ResourceController"));
        int port = runtime.port();
        assertEquals("resource", send("GET", "/resource").body());

        runtime.close();
        runtime = null;

        assertEquals(List.of("close Resource"), log.events());
        assertThrows(IOException.class, () -> client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/resource")).build(),
                BodyHandlers.ofString()));
    }

    // Fixtures

    public static final class Log {

        private final List<String> events = Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger probes = new AtomicInteger();

        void record(String event) {
            events.add(event);
        }

        List<String> events() {
            synchronized (events) {
                return List.copyOf(events);
            }
        }
    }

    public static final class HelloController extends Controller {

        @Override
        public void routes(Router routes) {
            routes.get("/hello", request -> "Hello World");
        }
    }

    public static final class OtherHelloController extends Controller {

        @Override
        public void routes(Router routes) {
            routes.get("/hello", request -> "Other");
        }
    }

    public static final class ItemsController extends Controller {

        @Override
        public void routes(Router routes) {
            routes.get("/items", request -> "GET items");
            routes.post("/items", request -> "POST items");
            routes.put("/items", request -> "PUT items");
            routes.patch("/items", request -> "PATCH items");
            routes.delete("/items", request -> "DELETE items");
            routes.get("/items/{id}", request -> "item " + request.pathParameter("id"));
        }
    }

    public static final class ApiController extends Controller {

        @Override
        public void routes(Router routes) {
            routes.group("/api", api -> {
                api.get("/health", request -> "healthy");
                api.group("/users", users -> users.get("/", request -> "users"));
            });
        }
    }

    public static final class ResultsController extends Controller {

        @Override
        public void routes(Router routes) {
            routes.post("/created", request -> Response.created("made").header("X-Resource", "7"));
            routes.get("/nothing", request -> null);
            routes.get("/unmapped", request -> List.of("json", "is", "M6"));
            routes.get("/failure", request -> {
                throw new IllegalStateException("internal detail");
            });
        }
    }

    public static final class MiddlewareController extends Controller {

        private final Log log;

        public MiddlewareController(Log log) {
            this.log = log;
        }

        @Override
        public void routes(Router routes) {
            routes.group("/admin", admin -> {
                admin.use((request, next) -> {
                    log.record("A before");
                    Response response = next.handle(request);
                    log.record("A after");
                    return response;
                });
                admin.use((request, next) -> {
                    log.record("B before");
                    Response response = next.handle(request);
                    log.record("B after");
                    return response;
                });
                admin.get("/users", request -> {
                    log.record("handler");
                    return "handler";
                });
            });
            routes.group("/guarded", guarded -> {
                guarded.use((request, next) -> Response.unauthorized("denied"));
                guarded.get("/secret", request -> {
                    log.record("secret");
                    return "secret";
                });
            });
        }
    }

    public static final class Probe implements AutoCloseable {

        private final Log log;
        private final int number;

        public Probe(Log log) {
            this.log = log;
            this.number = log.probes.incrementAndGet();
            log.record("open " + number);
        }

        @Override
        public void close() {
            log.record("close " + number);
        }
    }

    public static final class ScopeController extends Controller {

        @Override
        public void routes(Router routes) {
            routes.get("/scope", request -> "probe " + probe(request).number);
            routes.get("/scope/failure", request -> {
                probe(request);
                throw new IllegalStateException("handler failure");
            });
            routes.group("/scope/middleware", group -> {
                group.use((request, next) -> {
                    probe(request);
                    throw new IllegalStateException("middleware failure");
                });
                group.get("/", request -> "unreachable");
            });
        }

        private static Probe probe(Request request) {
            return ((RoutedRequest) request).scope().resolve(Probe.class);
        }
    }

    public interface Gateway {
    }

    public static final class StripeGateway implements Gateway {
    }

    public static final class PaypalGateway implements Gateway {
    }

    public static final class NeedsGatewayController extends Controller {

        public NeedsGatewayController(Gateway gateway) {
        }

        @Override
        public void routes(Router routes) {
            routes.get("/pay", request -> "paid");
        }
    }

    public static final class CycleA {

        public CycleA(CycleB b) {
        }
    }

    public static final class CycleB {

        public CycleB(CycleA a) {
        }
    }

    public static final class CycleController extends Controller {

        public CycleController(CycleA a) {
        }

        @Override
        public void routes(Router routes) {
            routes.get("/cycle", request -> "never");
        }
    }

    public static final class Resource implements AutoCloseable {

        private final Log log;

        public Resource(Log log) {
            this.log = log;
        }

        @Override
        public void close() {
            log.record("close Resource");
        }
    }

    public static final class ResourceController extends Controller {

        public ResourceController(Resource resource) {
        }

        @Override
        public void routes(Router routes) {
            routes.get("/resource", request -> "resource");
        }
    }

    public static final class BrokenUnrelated {

        public BrokenUnrelated() {
        }

        public BrokenUnrelated(Log log) {
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

    private static String controller(String name) {
        return "component %s" + name + "\nconstructor"
                + switch (name) {
                    case "MiddlewareController" -> " %sLog";
                    case "NeedsGatewayController" -> " %sGateway";
                    case "CycleController" -> " %sCycleA";
                    case "ResourceController" -> " %sResource";
                    default -> "";
                }
                + "\nsupertype org.dhole.web.Controller\n";
    }

    private static String component(String name) {
        return "component %s" + name + "\nconstructor %sLog\n";
    }

    private static String provider(String name) {
        return "component %s" + name + "\nconstructor\nsupertype %sGateway\n";
    }

    private ContainerBuilder components() {
        return ContainerBuilder.create()
                .bind(Log.class).toInstance(log)
                .component(Probe.class, ComponentScope.REQUEST);
    }

    private ComponentMetadata metadata(String... blocks) {
        String index = "dhole-metadata 1\n\n" + String.join("\n", blocks).replace("%s", PREFIX)
                + "\ncomponent " + PREFIX + "CycleA\nconstructor " + PREFIX + "CycleB\n"
                + "\ncomponent " + PREFIX + "CycleB\nconstructor " + PREFIX + "CycleA\n";
        try {
            Path file = indexDirectory.resolve("META-INF/dhole/components.idx");
            Files.createDirectories(file.getParent());
            Files.writeString(file, index, StandardCharsets.UTF_8);
            loader = new URLClassLoader(new URL[] {indexDirectory.toUri().toURL()}, getClass().getClassLoader());
            return ComponentMetadata.load(loader);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void start(String... blocks) {
        runtime = WebRuntime.start(metadata(blocks), components(), new JdkHttpServer("127.0.0.1", 0));
    }

    private HttpResponse<String> send(String method, String path) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + runtime.port() + path))
                .method(method, BodyPublishers.noBody()).build(), BodyHandlers.ofString());
    }
}
