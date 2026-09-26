package org.dhole.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class HttpPrimitivesTest {

    @Test
    void headerNamesAreCaseInsensitiveAndMultiValued() {
        Headers headers = Headers.of(Map.of("Accept", List.of("text/plain", "*/*"), "X-Trace", List.of("1")));

        assertEquals("text/plain", headers.first("accept"));
        assertEquals(List.of("text/plain", "*/*"), headers.all("ACCEPT"));
        assertEquals(Set.of("accept", "x-trace"), headers.names());
        assertNull(headers.first("Missing"));
        assertEquals(List.of(), headers.all("Missing"));
    }

    @Test
    void headersAreImmutable() {
        Headers original = Headers.empty();
        Headers extended = original.with("X-A", "1").with("x-a", "2");

        assertEquals(List.of(), original.all("X-A"));
        assertEquals(List.of("1", "2"), extended.all("X-A"));
    }

    @Test
    void responseHelpersSetTheDocumentedStatuses() {
        assertEquals(HttpStatus.OK, Response.ok().status());
        assertEquals("body", Response.ok("body").body());
        assertEquals(HttpStatus.CREATED, Response.created("x").status());
        assertEquals(HttpStatus.NO_CONTENT, Response.noContent().status());
        assertEquals(HttpStatus.BAD_REQUEST, Response.badRequest("x").status());
        assertEquals(HttpStatus.UNAUTHORIZED, Response.unauthorized("x").status());
        assertEquals(HttpStatus.FORBIDDEN, Response.forbidden("x").status());
        assertEquals(HttpStatus.NOT_FOUND, Response.notFound("x").status());
        assertEquals(405, Response.status(HttpStatus.METHOD_NOT_ALLOWED).status().code());
    }

    @Test
    void responsesAreImmutable() {
        Response original = Response.ok("a");
        Response changed = original.header("X-Resource", "1").body("b");

        assertEquals("a", original.body());
        assertNull(original.headers().first("X-Resource"));
        assertEquals("b", changed.body());
        assertEquals("1", changed.headers().first("x-resource"));
    }
}
