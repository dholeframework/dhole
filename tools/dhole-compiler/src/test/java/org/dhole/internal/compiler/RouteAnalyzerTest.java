package org.dhole.internal.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RouteAnalyzerTest {

    @TempDir
    Path output;

    private static final String APP = "package com.acme.shop; public final class App { private App() { } }";

    private static final String MODELS = """
            package com.acme.shop;

            public final class Models {
                private Models() { }
                public record User(long id, String name) { }
                public record CreateUser(String name) { }
            }
            """;

    @Test
    void typedGetRecordsPathParameterAndResponse() {
        TestCompiler.Result result = compile("""
                package com.acme.shop;

                import org.dhole.routing.Router;
                import org.dhole.web.Controller;

                public class UserController extends Controller {

                    @Override
                    public void routes(Router routes) {
                        routes.get("/users/{id}").to(this::find);
                    }

                    Models.User find(long id) {
                        return new Models.User(id, "user");
                    }
                }
                """);

        assertTrue(result.success(), result.diagnostics().toString());
        assertEquals(String.join("\n",
                "dhole-routes 1",
                "",
                "route com.acme.shop.UserController GET /users/{id}",
                "handler find",
                "parameter id PATH long",
                "response com.acme.shop.Models$User",
                "source com/acme/shop/UserController.java:10",
                ""), routes(result));
    }

    @Test
    void typedPostInfersTheBody() {
        TestCompiler.Result result = compile("""
                package com.acme.shop;

                import org.dhole.routing.Router;
                import org.dhole.web.Controller;

                public class UserController extends Controller {

                    @Override
                    public void routes(Router routes) {
                        routes.post("/users").to(this::create);
                    }

                    Models.User create(Models.CreateUser input) {
                        return new Models.User(1, input.name());
                    }
                }
                """);

        assertTrue(result.success(), result.diagnostics().toString());
        assertTrue(routes(result).contains("route com.acme.shop.UserController POST /users\nhandler create\n"
                + "parameter input BODY com.acme.shop.Models$CreateUser\n"), routes(result));
    }

    @Test
    void groupsWrappersRequestAndGenericsAreRecorded() {
        TestCompiler.Result result = compile("""
                package com.acme.shop;

                import java.util.List;
                import java.util.Map;
                import org.dhole.http.Request;
                import org.dhole.http.Response;
                import org.dhole.routing.Router;
                import org.dhole.web.Body;
                import org.dhole.web.Controller;
                import org.dhole.web.Header;
                import org.dhole.web.Query;

                public class ApiController extends Controller {

                    static final String USERS = "/users";

                    @Override
                    public void routes(Router routes) {
                        routes.group("/api", api -> api.group(USERS, users -> {
                            users.get("/").to(this::list);
                            users.put("/{id}").to(this::replace);
                            users.delete("/{id}").to(this::remove);
                        }));
                        routes.get("/health", request -> "raw routes are not recorded");
                    }

                    List<Models.User> list(Query<String> term, Header<Integer> pageSize) {
                        return List.of();
                    }

                    Map<String, List<Models.User>> replace(long id, Body<List<Models.CreateUser>> users, Request request) {
                        return Map.of();
                    }

                    Response remove(String id) {
                        return Response.noContent();
                    }
                }
                """);

        assertTrue(result.success(), result.diagnostics().toString());
        assertEquals(String.join("\n",
                "dhole-routes 1",
                "",
                "route com.acme.shop.ApiController GET /api/users",
                "handler list",
                "parameter term QUERY org.dhole.web.Query<java.lang.String>",
                "parameter pageSize HEADER org.dhole.web.Header<java.lang.Integer>",
                "response java.util.List<com.acme.shop.Models$User>",
                "source com/acme/shop/ApiController.java:20",
                "",
                "route com.acme.shop.ApiController DELETE /api/users/{id}",
                "handler remove",
                "parameter id PATH java.lang.String",
                "response org.dhole.http.Response",
                "source com/acme/shop/ApiController.java:22",
                "",
                "route com.acme.shop.ApiController PUT /api/users/{id}",
                "handler replace",
                "parameter id PATH long",
                "parameter users BODY org.dhole.web.Body<java.util.List<com.acme.shop.Models$CreateUser>>",
                "parameter request REQUEST org.dhole.http.Request",
                "response java.util.Map<java.lang.String,java.util.List<com.acme.shop.Models$User>>",
                "source com/acme/shop/ApiController.java:21",
                ""), routes(result));
    }

    @Test
    void rawRoutesOnlyProduceAnEmptyIndex() {
        TestCompiler.Result result = compile("""
                package com.acme.shop;

                import org.dhole.routing.Router;
                import org.dhole.web.Controller;

                public class HelloController extends Controller {

                    @Override
                    public void routes(Router routes) {
                        String dynamic = System.getProperty("path", "/hello");
                        routes.get(dynamic, request -> "Hello");
                    }
                }
                """);

        assertTrue(result.success(), result.diagnostics().toString());
        assertEquals("dhole-routes 1\n", routes(result));
    }

    @Test
    void nonConstantTypedPathFailsAtItsLine() {
        TestCompiler.Result result = compile("""
                package com.acme.shop;

                import org.dhole.routing.Router;
                import org.dhole.web.Controller;

                public class DynamicController extends Controller {

                    @Override
                    public void routes(Router routes) {
                        String path = System.getProperty("path");
                        routes.get(path).to(this::find);
                    }

                    String find() {
                        return "x";
                    }
                }
                """);

        assertFalse(result.success());
        assertEquals(1, result.diagnostics().size(), result.diagnostics().toString());
        assertTrue(result.diagnostics().get(0).startsWith("ERROR: Dhole Error DHOLE-ROUTE-001: The path of this typed GET"
                + " route cannot be determined at build time."), result.diagnostics().toString());
        assertTrue(result.diagnostics().get(0).endsWith("@ DynamicController.java:11"), result.diagnostics().toString());
    }

    @Test
    void typedRouteThroughAnUnknownRouterFails() {
        TestCompiler.Result result = compile("""
                package com.acme.shop;

                import org.dhole.routing.Router;
                import org.dhole.web.Controller;

                public class HelperController extends Controller {

                    @Override
                    public void routes(Router routes) {
                        register(routes);
                    }

                    private void register(Router router) {
                        router.get("/x").to(this::find);
                    }

                    String find() {
                        return "x";
                    }
                }
                """);

        // The helper is outside routes(): nothing is recorded, and the runtime reports the missing
        // metadata at startup. Only routes() bodies are analyzed.
        assertTrue(result.success(), result.diagnostics().toString());
        assertEquals("dhole-routes 1\n", routes(result));
    }

    @Test
    void handlerThatIsNotAControllerMethodReferenceFails() {
        TestCompiler.Result result = compile("""
                package com.acme.shop;

                import org.dhole.routing.Router;
                import org.dhole.web.Controller;

                public class LambdaController extends Controller {

                    @Override
                    public void routes(Router routes) {
                        routes.get("/x").to(() -> "x");
                    }
                }
                """);

        assertEquals(List.of("ERROR: Dhole Error DHOLE-ROUTE-002: The handler of typed route GET /x must be a method of"
                + " this controller: .to(this::method). @ LambdaController.java:10"), result.diagnostics());
    }

    @Test
    void bindingProblemsFailWithTheirCodesAndLocation() {
        TestCompiler.Result result = compile("""
                package com.acme.shop;

                import org.dhole.routing.Router;
                import org.dhole.web.Controller;
                import org.dhole.web.Path;

                public class BrokenController extends Controller {

                    @Override
                    public void routes(Router routes) {
                        routes.get("/search").to(this::search);
                        routes.get("/users/{id}").to(this::unused);
                        routes.get("/items").to(this::byPath);
                        routes.post("/pairs").to(this::pair);
                        routes.get("/bodies").to(this::getBody);
                    }

                    String search(String term) { return term; }
                    String unused() { return "x"; }
                    String byPath(Path<Long> id) { return "x"; }
                    String pair(Models.CreateUser a, Models.CreateUser b) { return "x"; }
                    String getBody(Models.CreateUser input) { return "x"; }
                }
                """);

        assertFalse(result.success());
        List<String> diagnostics = result.diagnostics();
        assertEquals(5, diagnostics.size(), diagnostics.toString());
        assertTrue(diagnostics.get(0).startsWith("ERROR: Dhole Error DHOLE-BIND-001: Cannot determine the source of "
                + "parameter 'term' (java.lang.String) of route GET /search"), diagnostics.get(0));
        assertTrue(diagnostics.get(0).contains("Use Query<T>, Header<T>, Path<T> or Body<T>."), diagnostics.get(0));
        assertTrue(diagnostics.get(0).endsWith("@ BrokenController.java:11"), diagnostics.get(0));
        assertTrue(diagnostics.get(1).contains("DHOLE-BIND-002: Placeholder {id} of route GET /users/{id} has no "
                + "handler parameter named 'id'.") && diagnostics.get(1).endsWith(":12"), diagnostics.get(1));
        assertTrue(diagnostics.get(2).contains("DHOLE-BIND-002: Parameter 'id' of route GET /items is a Path<T>")
                && diagnostics.get(2).endsWith(":13"), diagnostics.get(2));
        assertTrue(diagnostics.get(3).contains("DHOLE-BIND-003: Route POST /pairs has more than one structured "
                + "parameter (a, b)") && diagnostics.get(3).endsWith(":14"), diagnostics.get(3));
        assertTrue(diagnostics.get(4).contains("DHOLE-BIND-001") && diagnostics.get(4).contains(
                "the body is only inferred on POST, PUT and PATCH; use Body<T> to read it on GET")
                && diagnostics.get(4).endsWith(":15"), diagnostics.get(4));
    }

    @Test
    void routeIndexIsByteForByteDeterministic(@TempDir Path first, @TempDir Path second) throws IOException {
        String a = """
                package com.acme.shop;
                import org.dhole.routing.Router;
                import org.dhole.web.Controller;
                public class AController extends Controller {
                    public void routes(Router routes) {
                        routes.post("/a").to(this::post);
                        routes.get("/a/{id}").to(this::get);
                    }
                    String post(Models.CreateUser input) { return "x"; }
                    String get(long id) { return "x"; }
                }
                """;
        String b = """
                package com.acme.shop;
                import org.dhole.routing.Router;
                import org.dhole.web.Controller;
                public class BController extends Controller {
                    public void routes(Router routes) {
                        routes.get("/b").to(this::get);
                    }
                    String get() { return "x"; }
                }
                """;
        base().source("com.acme.shop.AController", a).source("com.acme.shop.BController", b).compile(first);
        base().source("com.acme.shop.BController", b).source("com.acme.shop.AController", a).compile(second);

        assertEquals(-1L, Files.mismatch(first.resolve(RouteIndexWriter.LOCATION), second.resolve(RouteIndexWriter.LOCATION)));
        String index = Files.readString(first.resolve(RouteIndexWriter.LOCATION));
        int postA = index.indexOf("AController POST /a\n");
        int getA = index.indexOf("AController GET /a/{id}\n");
        int getB = index.indexOf("BController GET /b\n");
        assertTrue(postA > 0 && postA < getA && getA < getB, index);
    }

    private TestCompiler base() {
        return TestCompiler.create()
                .source("com.acme.shop.App", APP)
                .source("com.acme.shop.Models", MODELS)
                .application("com.acme.shop.App");
    }

    private TestCompiler.Result compile(String controller) {
        String name = controller.substring(controller.indexOf("public class ") + 13, controller.indexOf(" extends"));
        return base().source("com.acme.shop." + name, controller).compile(output);
    }

    private String routes(TestCompiler.Result result) {
        try {
            return Files.readString(result.output().resolve(RouteIndexWriter.LOCATION), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
