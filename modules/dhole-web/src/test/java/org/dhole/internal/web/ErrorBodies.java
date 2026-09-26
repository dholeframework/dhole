package org.dhole.internal.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Assertions on JSON error envelopes; the generated request ID is checked and then replaced by
 * {@code "*"} so envelopes compare exactly.
 */
final class ErrorBodies {

    private static final Pattern REQUEST_ID = Pattern.compile("\"requestId\":\"([0-9a-f-]{36})\"");

    private ErrorBodies() {
    }

    /**
     * Asserts status, JSON content type and that the envelope's request ID equals the
     * {@code X-Request-Id} header; returns the body with the ID replaced by {@code *}.
     */
    static String envelope(HttpResponse<String> response, int status) {
        assertEquals(status, response.statusCode(), response.body());
        assertEquals(Optional.of("application/json"), response.headers().firstValue("Content-Type"));
        Matcher matcher = REQUEST_ID.matcher(response.body());
        assertTrue(matcher.find(), response.body());
        assertEquals(response.headers().firstValue(RequestPipeline.REQUEST_ID), Optional.of(matcher.group(1)));
        return matcher.replaceFirst("\"requestId\":\"*\"");
    }

    static String error(String code, String message) {
        return "{\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"requestId\":\"*\"}}";
    }
}
