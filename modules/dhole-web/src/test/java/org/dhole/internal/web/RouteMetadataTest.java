package org.dhole.internal.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.dhole.http.HttpMethod;
import org.dhole.routing.RoutingException;
import org.dhole.serialization.TypeRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RouteMetadataTest {

    private static final ClassLoader LOADER = RouteMetadataTest.class.getClassLoader();

    @Test
    void parsesTheDocumentedFormat() {
        RouteMetadata metadata = RouteMetadata.parse("""
                dhole-routes 1

                route com.acme.UserController GET /users/{id}
                handler find
                parameter id PATH long
                response com.acme.User
                source com/acme/UserController.java:12

                route com.acme.UserController POST /users
                handler create
                parameter input BODY com.acme.CreateUser
                parameter request REQUEST org.dhole.http.Request
                response java.util.List<com.acme.User>
                """);

        RouteMetadata.Entry find = metadata.route("com.acme.UserController", HttpMethod.GET, "/users/{id}").orElseThrow();
        assertEquals("find", find.handler());
        assertEquals(List.of(new RouteMetadata.Parameter("id", ParameterSource.PATH, "long")), find.parameters());
        assertEquals("com.acme.User", find.response());
        assertEquals(Optional.of("com/acme/UserController.java:12"), find.source());
        RouteMetadata.Entry create = metadata.route("com.acme.UserController", HttpMethod.POST, "/users").orElseThrow();
        assertEquals(2, create.parameters().size());
        assertEquals(Optional.empty(), create.source());
        assertEquals(Optional.empty(), metadata.route("com.acme.UserController", HttpMethod.GET, "/users"));
        assertEquals(Optional.empty(), metadata.route("com.acme.Other", HttpMethod.GET, "/users/{id}"));
    }

    @Test
    void unknownVersionIsACompatibilityError() {
        RoutingException failure = assertThrows(RoutingException.class, () -> RouteMetadata.parse("dhole-routes 2\n"));

        assertEquals("Metadata Compatibility Error\n\nApplication route metadata version: 2\nRuntime supports: 1\n\n"
                + "Rebuild the application using a compatible Dhole build tool.", failure.getMessage());
    }

    @Test
    void malformedIndexesFailWithTheirLine() {
        assertMalformed("routes 1\n", 1);
        assertMalformed("dhole-routes 1\n\nroute X TRACE /x\nhandler h\nresponse void\n", 3);
        assertMalformed("dhole-routes 1\n\nroute X GET /x\nresponse void\n", 4);
        assertMalformed("dhole-routes 1\n\nroute X GET /x\nhandler h\nparameter a COOKIE String\nresponse void\n", 5);
        assertMalformed("dhole-routes 1\n\nroute X GET /x\nhandler h\n", 5);
        assertMalformed("dhole-routes 1\n\nroute X GET /x\nhandler h\nresponse void\nextra line\n", 6);
        assertMalformed("dhole-routes 1\n\nroute X GET /x\nhandler h\nresponse void\n\nroute X GET /x\nhandler h\n"
                + "response void\n", 7);
    }

    @Test
    void missingIndexIsEmptyAndIndexIsLoadedFromTheClassLoader(@TempDir Path directory) throws IOException {
        try (URLClassLoader empty = new URLClassLoader(new URL[] {directory.toUri().toURL()}, null)) {
            assertEquals(Optional.empty(), RouteMetadata.load(empty).route("X", HttpMethod.GET, "/x"));
        }
        Path index = directory.resolve(RouteMetadata.LOCATION);
        Files.createDirectories(index.getParent());
        Files.writeString(index, "dhole-routes 1\n\nroute X GET /x\nhandler h\nresponse void\n");
        try (URLClassLoader loader = new URLClassLoader(new URL[] {directory.toUri().toURL()}, null)) {
            assertEquals("h", RouteMetadata.load(loader).route("X", HttpMethod.GET, "/x").orElseThrow().handler());
        }
    }

    @Test
    void typeNamesAreParsedIntoGenericTypes() throws ClassNotFoundException {
        assertEquals(long.class, TypeNames.parse("long", LOADER));
        assertEquals(String[].class, TypeNames.parse("java.lang.String[]", LOADER));
        assertEquals(RouteMetadataTest.class, TypeNames.parse("org.dhole.internal.web.RouteMetadataTest", LOADER));

        Type map = TypeNames.parse("java.util.Map<java.lang.String,java.util.List<java.lang.Long>>", LOADER);
        assertEquals(new TypeRef<Map<String, List<Long>>>() {
        }.type(), map);
        assertEquals(List.class, ((ParameterizedType) TypeNames.parse("java.util.List<java.lang.String>", LOADER)).getRawType());
        assertTrue(TypeNames.parse("java.util.List<java.lang.String>[]", LOADER) instanceof GenericArrayType);
    }

    @Test
    void malformedOrUnknownTypeNamesFail() {
        for (String name : List.of("", "java.util.List<", "java.util.List<java.lang.String", "java.util.Map<java.lang.String>",
                "long]", "java.util.List<java.lang.String>>")) {
            assertThrows(IllegalArgumentException.class, () -> TypeNames.parse(name, LOADER), name);
        }
        assertThrows(ClassNotFoundException.class, () -> TypeNames.parse("com.example.Gone", LOADER));
    }

    private static void assertMalformed(String text, int line) {
        RoutingException failure = assertThrows(RoutingException.class, () -> RouteMetadata.parse(text), text);
        assertTrue(failure.getMessage().startsWith("Metadata Error\n\nMalformed META-INF/dhole/routes.idx at line "
                + line + ":"), failure.getMessage());
        assertTrue(failure.getMessage().endsWith("Rebuild the application."), failure.getMessage());
    }
}
