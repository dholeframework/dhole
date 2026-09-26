package org.dhole.internal.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
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
import java.util.Map;

import org.dhole.http.HttpStatus;
import org.dhole.http.Response;
import org.dhole.internal.di.ComponentMetadata;
import org.dhole.internal.di.ComponentScope;
import org.dhole.internal.di.ContainerBuilder;
import org.dhole.internal.http.JdkHttpServer;
import org.dhole.internal.validation.ValidationMetadata;
import org.dhole.routing.Router;
import org.dhole.validation.Rules;
import org.dhole.validation.Validatable;
import org.dhole.validation.Validator;
import org.dhole.web.AppException;
import org.dhole.web.Controller;
import org.dhole.web.ErrorResponse;
import org.dhole.web.Errors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Error mapping, request IDs and HTTP validation over real HTTP (ERRORS.md §12, VALIDATION.md §20,
 * REQUEST_LIFECYCLE.md §36).
 */
class ErrorHandlingTest {

    private static final String PREFIX = "org.dhole.internal.web.ErrorHandlingTest$";
    private static final String CONTROLLER = PREFIX + "OrderController";

    private static final String ROUTES = """
            dhole-routes 1

            route %1$s POST /orders
            handler create
            parameter input BODY %2$sCreateOrder
            response %2$sOrder
            """;

    private static final String VALIDATION = """
            dhole-validation 1

            type %1$sCreateOrder
            field product java.lang.String
            field quantity int
            field email java.lang.String
            """;

    @TempDir
    Path indexDirectory;

    private final HttpClient client = HttpClient.newHttpClient();
    private final Log log = new Log();
    private final ByteArrayOutputStream errorLog = new ByteArrayOutputStream();
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

    // Application errors

    @Test
    void errorsFactoriesMapToTheirStatusAndCode() throws Exception {
        start(WebRuntime.Options.defaults());

        assertEquals(ErrorBodies.error("NOT_FOUND", "Order not found."), ErrorBodies.envelope(send("GET", "/errors/not-found"), 404));
        assertEquals(ErrorBodies.error("BAD_REQUEST", "Bad input."), ErrorBodies.envelope(send("GET", "/errors/bad-request"), 400));
        assertEquals(ErrorBodies.error("UNAUTHORIZED", "Sign in."), ErrorBodies.envelope(send("GET", "/errors/unauthorized"), 401));
        assertEquals(ErrorBodies.error("FORBIDDEN", "Not yours."), ErrorBodies.envelope(send("GET", "/errors/forbidden"), 403));
        assertEquals(ErrorBodies.error("CONFLICT", "Already paid."), ErrorBodies.envelope(send("GET", "/errors/conflict"), 409));
        assertEquals(ErrorBodies.error("INSUFFICIENT_BALANCE", "Insufficient balance."),
                ErrorBodies.envelope(send("GET", "/errors/domain"), 409));
        assertEquals("", errorLog.toString(StandardCharsets.UTF_8), "expected errors are not logged as unexpected");
    }

    @Test
    void specificErrorHandlersWinOverGeneralOnes() throws Exception {
        start(WebRuntime.Options.defaults().errors(ErrorHandlerRegistry.defaults()
                .handle(InsufficientBalance.class, error -> Response.status(HttpStatus.UNPROCESSABLE_CONTENT)
                        .body(ErrorResponse.of("PAYMENT_DECLINED", "Payment declined.").details(Map.of("retry", false))))
                .handle(IllegalArgumentException.class, error -> Response.badRequest(
                        ErrorResponse.of("ILLEGAL_ARGUMENT", "Illegal argument.")))));

        assertEquals("{\"error\":{\"code\":\"PAYMENT_DECLINED\",\"message\":\"Payment declined.\","
                + "\"details\":{\"retry\":false},\"requestId\":\"*\"}}", ErrorBodies.envelope(send("GET", "/errors/domain"), 422));
        assertEquals(ErrorBodies.error("CONFLICT", "Already paid."), ErrorBodies.envelope(send("GET", "/errors/conflict"), 409));
        assertEquals(ErrorBodies.error("ILLEGAL_ARGUMENT", "Illegal argument."),
                ErrorBodies.envelope(send("GET", "/errors/illegal"), 400));
        assertThrows(IllegalStateException.class,
                () -> ErrorHandlerRegistry.defaults().handle(AppException.class, error -> Response.ok()));
    }

    @Test
    void handlersMayReturnAnErrorResponseBody() throws Exception {
        start(WebRuntime.Options.defaults());

        assertEquals(ErrorBodies.error("OUT_OF_STOCK", "Out of stock."), ErrorBodies.envelope(send("GET", "/errors/returned"), 409));
    }

    // Unexpected errors

    @Test
    void productionHidesUnexpectedErrorsAndLogsThemWithTheRequestId() throws Exception {
        start(WebRuntime.Options.defaults());

        HttpResponse<String> response = send("GET", "/errors/unexpected");

        assertEquals(ErrorBodies.error("INTERNAL_ERROR", "An unexpected error occurred."), ErrorBodies.envelope(response, 500));
        assertFalse(response.body().contains("IllegalStateException") || response.body().contains("secret-token"),
                response.body());
        String logged = errorLog.toString(StandardCharsets.UTF_8);
        String requestId = response.headers().firstValue(RequestPipeline.REQUEST_ID).orElseThrow();
        assertTrue(logged.startsWith("Unexpected error [requestId=" + requestId + "] GET /errors/unexpected"), logged);
        assertTrue(logged.contains("java.lang.IllegalStateException") && logged.contains("\tat "), logged);
    }

    @Test
    void developmentAddsOnlyTheExceptionTypeAndMessage() throws Exception {
        start(WebRuntime.Options.defaults().mode(RuntimeMode.DEVELOPMENT));

        HttpResponse<String> response = send("GET", "/errors/unexpected");

        assertEquals("{\"error\":{\"code\":\"INTERNAL_ERROR\",\"message\":\"An unexpected error occurred.\","
                + "\"details\":{\"exception\":\"java.lang.IllegalStateException\",\"message\":\"Inventory offline\"},"
                + "\"requestId\":\"*\"}}", ErrorBodies.envelope(response, 500));
        assertFalse(response.body().contains("\tat ") || response.body().contains("ErrorHandlingTest"), response.body());
        assertTrue(errorLog.toString(StandardCharsets.UTF_8).contains("\tat "));
    }

    @Test
    void failingErrorHandlerIsAnUnexpectedError() throws Exception {
        start(WebRuntime.Options.defaults().errors(ErrorHandlerRegistry.defaults()
                .handle(IllegalArgumentException.class, error -> {
                    throw new IllegalStateException("handler broke");
                })));

        assertEquals(ErrorBodies.error("INTERNAL_ERROR", "An unexpected error occurred."),
                ErrorBodies.envelope(send("GET", "/errors/illegal"), 500));
    }

    @Test
    void requestScopeIsClosedWhenTheHandlerFails() throws Exception {
        start(WebRuntime.Options.defaults());

        send("GET", "/errors/scoped");

        assertEquals(List.of("open probe", "close probe"), log.events());
    }

    // Request ID

    @Test
    void everyResponseHasAFreshRequestIdAndClientValuesAreIgnored() throws Exception {
        start(WebRuntime.Options.defaults());

        HttpResponse<String> first = client.send(HttpRequest.newBuilder(uri("/ok"))
                .header("X-Request-Id", "client-chosen").build(), BodyHandlers.ofString());
        HttpResponse<String> second = send("GET", "/ok");

        String id = first.headers().firstValue(RequestPipeline.REQUEST_ID).orElseThrow();
        assertTrue(id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"), id);
        assertNotEquals(id, second.headers().firstValue(RequestPipeline.REQUEST_ID).orElseThrow());
        assertEquals(List.of(id), first.headers().allValues(RequestPipeline.REQUEST_ID));
        ErrorBodies.envelope(send("GET", "/missing"), 404);
    }

    // Validation

    @Test
    void invalidBodyIs422WithFieldsAndTheHandlerDoesNotRun() throws Exception {
        start(WebRuntime.Options.defaults());

        HttpResponse<String> response = post("{\"product\":\" \",\"quantity\":0,\"email\":\"nope\"}");

        assertEquals("{\"error\":{\"code\":\"VALIDATION_ERROR\",\"message\":\"The request contains invalid fields.\","
                + "\"fields\":{\"product\":[{\"code\":\"NOT_BLANK\",\"message\":\"Must not be blank.\"}],"
                + "\"quantity\":[{\"code\":\"POSITIVE\",\"message\":\"Must be positive.\"}],"
                + "\"email\":[{\"code\":\"INVALID_EMAIL\",\"message\":\"Must be a valid email address.\"}]},"
                + "\"requestId\":\"*\"}}", ErrorBodies.envelope(response, 422));
        assertFalse(response.body().contains("nope"), "input values are not echoed");
        assertEquals(List.of(), log.events());
    }

    @Test
    void validBodyReachesTheHandler() throws Exception {
        start(WebRuntime.Options.defaults());

        HttpResponse<String> response = post("{\"product\":\"book\",\"quantity\":2,\"email\":\"a@b.co\"}");

        assertEquals(200, response.statusCode(), response.body());
        assertEquals("{\"product\":\"book\",\"quantity\":2}", response.body());
        assertEquals(List.of("create book"), log.events());
    }

    @Test
    void bindingFailuresStay400AndAreDistinctFrom422() throws Exception {
        start(WebRuntime.Options.defaults());

        HttpResponse<String> malformed = post("{\"product\":");

        assertEquals(ErrorBodies.error("BAD_REQUEST", "Invalid request body: Malformed JSON"), ErrorBodies.envelope(malformed, 400));
        assertEquals(ErrorBodies.error("UNSUPPORTED_MEDIA_TYPE", "The request Content-Type is not supported."),
                ErrorBodies.envelope(client.send(HttpRequest.newBuilder(uri("/orders"))
                        .POST(BodyPublishers.ofString("x")).header("Content-Type", "text/plain").build(),
                        BodyHandlers.ofString()), 415));
    }

    @Test
    void validatorIsInjectable() throws Exception {
        start(WebRuntime.Options.defaults());

        assertEquals("REQUIRED", send("GET", "/validate-manually").body());
    }

    @Test
    void missingValidationMetadataFailsStartup() {
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> WebRuntime.start(components(),
                RouteMetadata.parse(String.format(ROUTES, CONTROLLER, PREFIX)), builder(),
                new JdkHttpServer("127.0.0.1", 0), WebRuntime.Options.defaults()));

        assertTrue(failure.getMessage().endsWith("Rebuild the application."), failure.getMessage());
    }

    // Fixtures

    public record CreateOrder(String product, int quantity, String email) implements Validatable {

        public static Rules<CreateOrder> rules() {
            return Rules.forType(CreateOrder.class)
                    .field(CreateOrder::product).required().notBlank()
                    .field(CreateOrder::quantity).positive()
                    .field(CreateOrder::email).email();
        }
    }

    public record Order(String product, int quantity) {
    }

    public static final class InsufficientBalance extends AppException {

        private static final long serialVersionUID = 1L;

        public InsufficientBalance() {
            super("INSUFFICIENT_BALANCE", HttpStatus.CONFLICT, "Insufficient balance.");
        }
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

    public static final class OrderController extends Controller {

        private final Log log;
        private final Validator validator;

        public OrderController(Log log, Validator validator) {
            this.log = log;
            this.validator = validator;
        }

        @Override
        public void routes(Router routes) {
            routes.post("/orders").to(this::create);
            routes.get("/ok", request -> "ok");
            routes.get("/validate-manually", request -> validator.validate(new CreateOrder(null, 1, null))
                    .errors().get(0).code());
            routes.group("/errors", errors -> {
                errors.get("/not-found", request -> {
                    throw Errors.notFound("Order");
                });
                errors.get("/bad-request", request -> {
                    throw Errors.badRequest("Bad input.");
                });
                errors.get("/unauthorized", request -> {
                    throw Errors.unauthorized("Sign in.");
                });
                errors.get("/forbidden", request -> {
                    throw Errors.forbidden("Not yours.");
                });
                errors.get("/conflict", request -> {
                    throw Errors.conflict("Already paid.");
                });
                errors.get("/domain", request -> {
                    throw new InsufficientBalance();
                });
                errors.get("/illegal", request -> {
                    throw new IllegalArgumentException("illegal");
                });
                errors.get("/unexpected", request -> {
                    throw new IllegalStateException("Inventory offline");
                });
                errors.get("/returned", request -> Response.conflict(ErrorResponse.of("OUT_OF_STOCK", "Out of stock.")));
                errors.get("/scoped", request -> {
                    ((RoutedRequest) request).scope().resolve(Probe.class);
                    throw Errors.conflict("Scoped failure.");
                });
            });
        }

        Order create(CreateOrder input) {
            log.record("create " + input.product());
            return new Order(input.product(), input.quantity());
        }
    }

    // Helpers

    private void start(WebRuntime.Options options) {
        runtime = WebRuntime.start(components(), RouteMetadata.parse(String.format(ROUTES, CONTROLLER, PREFIX)), builder(),
                new JdkHttpServer("127.0.0.1", 0), options
                        .validation(ValidationMetadata.parse(String.format(VALIDATION, PREFIX)))
                        .log(new PrintStream(errorLog, true, StandardCharsets.UTF_8)));
    }

    private ContainerBuilder builder() {
        return ContainerBuilder.create()
                .bind(Log.class).toInstance(log)
                .component(Probe.class, ComponentScope.REQUEST);
    }

    private ComponentMetadata components() {
        String index = "dhole-metadata 1\n\ncomponent " + CONTROLLER + "\nconstructor " + PREFIX + "Log "
                + "org.dhole.validation.Validator\nsupertype org.dhole.web.Controller\n";
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

    private HttpResponse<String> post(String body) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(uri("/orders")).POST(BodyPublishers.ofString(body))
                .header("Content-Type", "application/json").build(), BodyHandlers.ofString());
    }

    private HttpResponse<String> send(String method, String path) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(uri(path)).method(method, BodyPublishers.noBody()).build(),
                BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + runtime.port() + path);
    }
}
