package org.dhole.internal.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dhole.http.HttpMethod;
import org.dhole.routing.Handler;
import org.dhole.routing.Middleware;
import org.dhole.routing.RouteDefinition;
import org.dhole.routing.Router;
import org.dhole.routing.RoutingException;
import org.junit.jupiter.api.Test;

class RoutingTest {

    private static final Handler HANDLER = request -> "ok";

    private final RouteRegistry registry = new RouteRegistry();
    private final Router routes = registry.router("TestController");

    // Methods

    @Test
    void everyMethodIsRegisteredAndMatched() {
        routes.get("/items", HANDLER);
        routes.post("/items", HANDLER);
        routes.put("/items", HANDLER);
        routes.patch("/items", HANDLER);
        routes.delete("/items", HANDLER);
        RouteMatcher matcher = registry.build();

        for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH,
                HttpMethod.DELETE)) {
            RouteMatch.Found found = assertInstanceOf(RouteMatch.Found.class, matcher.match(method, "/items"));
            assertEquals(method, found.route().method());
        }
    }

    @Test
    void differentMethodsOnTheSamePathHaveTheirOwnHandlers() {
        Handler get = request -> "get";
        Handler post = request -> "post";
        routes.get("/items", get);
        routes.post("/items", post);
        RouteMatcher matcher = registry.build();

        assertEquals(get, found(matcher, HttpMethod.GET, "/items").route().handler());
        assertEquals(post, found(matcher, HttpMethod.POST, "/items").route().handler());
    }

    @Test
    void registrationReturnsTheRouteDefinition() {
        RouteDefinition route = routes.get("/items/{id}", HANDLER);

        assertEquals(HttpMethod.GET, route.method());
        assertEquals("/items/{id}", route.path());
    }

    // 404 and 405

    @Test
    void unknownPathIsNotFound() {
        routes.get("/items", HANDLER);

        assertInstanceOf(RouteMatch.NotFound.class, registry.build().match(HttpMethod.GET, "/other"));
    }

    @Test
    void knownPathWithUnregisteredMethodIsMethodNotAllowed() {
        routes.get("/items/{id}", HANDLER);
        routes.delete("/items/{id}", HANDLER);

        RouteMatch match = registry.build().match(HttpMethod.POST, "/items/7");

        assertEquals(new RouteMatch.MethodNotAllowed(Set.of(HttpMethod.GET, HttpMethod.DELETE)), match);
    }

    @Test
    void trailingSlashIsNotNormalized() {
        routes.get("/hello", HANDLER);
        RouteMatcher matcher = registry.build();

        assertInstanceOf(RouteMatch.Found.class, matcher.match(HttpMethod.GET, "/hello"));
        assertInstanceOf(RouteMatch.NotFound.class, matcher.match(HttpMethod.GET, "/hello/"));
        assertInstanceOf(RouteMatch.NotFound.class, matcher.match(HttpMethod.GET, "//hello"));
    }

    @Test
    void rootRouteMatchesOnlyTheRoot() {
        routes.get("/", HANDLER);
        RouteMatcher matcher = registry.build();

        assertInstanceOf(RouteMatch.Found.class, matcher.match(HttpMethod.GET, "/"));
        assertInstanceOf(RouteMatch.NotFound.class, matcher.match(HttpMethod.GET, "/x"));
    }

    // Parameters

    @Test
    void pathParametersAreExtractedRawAndDecoded() {
        routes.get("/users/{id}/files/{name}", HANDLER);

        RouteMatch.Found found = found(registry.build(), HttpMethod.GET, "/users/42/files/a%20b%2Fc");

        assertEquals(Map.of("id", "42", "name", "a b/c"), found.parameters());
    }

    @Test
    void malformedPercentEncodingIsNotFound() {
        routes.get("/users/{id}", HANDLER);

        assertInstanceOf(RouteMatch.NotFound.class, registry.build().match(HttpMethod.GET, "/users/%G1"));
    }

    @Test
    void literalSegmentsWinOverParametersRegardlessOfRegistrationOrder() {
        Handler me = request -> "me";
        Handler byId = request -> "id";
        routes.get("/users/{id}", byId);
        routes.get("/users/me", me);
        routes.get("/{section}/items", HANDLER);
        RouteMatcher matcher = registry.build();

        assertEquals(me, found(matcher, HttpMethod.GET, "/users/me").route().handler());
        assertEquals(byId, found(matcher, HttpMethod.GET, "/users/7").route().handler());
        // Precedence is decided at the first differing position: literal "users" beats {section}.
        assertEquals("/users/{id}", found(matcher, HttpMethod.GET, "/users/items").route().path());
        assertEquals("/{section}/items", found(matcher, HttpMethod.GET, "/shop/items").route().path());
    }

    // Conflicts

    @Test
    void duplicateRouteIsRejectedBeforeServing() {
        routes.get("/users/{id}", HANDLER);
        registry.router("LegacyController").get("/users/{userId}", HANDLER);

        RoutingException failure = assertThrows(RoutingException.class, registry::build);

        assertEquals("Routing Error\n\nDuplicate route:\nGET /users/{userId}\n\nDeclared by:\n"
                + "LegacyController\nTestController (/users/{id})", failure.getMessage());
    }

    @Test
    void sameShapeWithDifferentMethodsIsNotAConflict() {
        routes.get("/users/{id}", HANDLER);
        routes.put("/users/{id}", HANDLER);

        assertEquals(2, registry.build().routes().size());
    }

    @Test
    void registrationClosesWhenTheRoutesAreBuilt() {
        registry.build();

        assertThrows(RoutingException.class, () -> routes.get("/late", HANDLER));
    }

    @Test
    void malformedPathsAreRejected() {
        for (String path : List.of("hello", "", "/users/", "/a//b", "/users/{id", "/users/{id}x", "/users/{}",
                "/users/{1id}", "/a/{id}/b/{id}", "/search?q", "/a#b")) {
            RoutingException failure = assertThrows(RoutingException.class, () -> routes.get(path, HANDLER), path);
            assertTrue(failure.getMessage().startsWith("Routing Error\n\nInvalid route path:"), failure.getMessage());
        }
    }

    // Groups and middleware

    @Test
    void groupsComposePrefixesWithoutDuplicateSlashes() {
        routes.group("/api", api -> {
            api.get("/health", HANDLER);
            api.group("/users", users -> {
                users.get("/", HANDLER);
                users.get("/{id}", HANDLER);
                users.group("/{id}/posts", posts -> posts.get("/", HANDLER));
            });
        });
        routes.group("/", root -> root.get("/root", HANDLER));

        assertEquals(List.of("/api/health", "/api/users", "/api/users/{id}", "/api/users/{id}/posts", "/root"),
                registry.build().routes().stream().map(RouteDefinition::path).sorted().toList());
    }

    @Test
    void parameterNamesMustBeUniqueAcrossGroups() {
        routes.group("/users/{id}", users -> assertThrows(RoutingException.class, () -> users.get("/{id}", HANDLER)));
    }

    @Test
    void middlewareAppliesOutermostFirstAndOnlyInsideItsGroup() {
        Middleware outer = (request, next) -> next.handle(request);
        Middleware inner = (request, next) -> next.handle(request);
        Middleware second = (request, next) -> next.handle(request);
        routes.use(outer);
        routes.get("/plain", HANDLER);
        assertThrows(RoutingException.class, () -> routes.use(second));
        routes.group("/admin", admin -> {
            admin.use(inner);
            admin.use(second);
            admin.get("/users", HANDLER);
        });
        registry.router("Other").get("/other", HANDLER);
        RouteMatcher matcher = registry.build();

        assertEquals(List.of(outer), found(matcher, HttpMethod.GET, "/plain").route().middleware());
        assertEquals(List.of(outer, inner, second), found(matcher, HttpMethod.GET, "/admin/users").route().middleware());
        assertEquals(List.of(), found(matcher, HttpMethod.GET, "/other").route().middleware());
    }

    @Test
    void middlewareDeclaredAfterRoutesInAGroupIsRejected() {
        routes.group("/admin", admin -> {
            admin.get("/users", HANDLER);
            RoutingException failure = assertThrows(RoutingException.class,
                    () -> admin.use((request, next) -> next.handle(request)));
            assertEquals("Routing Error\n\nMiddleware must be declared before the routes and groups it applies to "
                    + "in group /admin.", failure.getMessage());
        });
    }

    private static RouteMatch.Found found(RouteMatcher matcher, HttpMethod method, String path) {
        return assertInstanceOf(RouteMatch.Found.class, matcher.match(method, path));
    }
}
