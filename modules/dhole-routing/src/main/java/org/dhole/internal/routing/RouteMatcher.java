package org.dhole.internal.routing;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.dhole.http.HttpMethod;

/**
 * Matches requests against validated routes. Deterministic and independent of registration
 * order: among the routes whose template matches the path, the one for the request method with the
 * most specific template wins (literal segments before parameters). A {@code HEAD} request uses the
 * {@code GET} route of the path (the server sends no body); routes cannot be registered for
 * {@code HEAD} itself.
 */
public final class RouteMatcher {

    private final List<Route> routes;

    RouteMatcher(List<Route> routes) {
        this.routes = routes.stream()
                .sorted(Comparator.comparing(Route::template, RouteTemplate::bySpecificity)
                        .thenComparing(Route::method))
                .toList();
    }

    public List<Route> routes() {
        return routes;
    }

    /**
     * @param rawPath the request path, percent-encoded
     */
    public RouteMatch match(HttpMethod method, String rawPath) {
        Objects.requireNonNull(method, "method");
        HttpMethod routeMethod = method == HttpMethod.HEAD ? HttpMethod.GET : method;
        Optional<List<String>> segments = decode(rawPath);
        if (segments.isEmpty()) {
            return new RouteMatch.NotFound();
        }
        Set<HttpMethod> allowed = EnumSet.noneOf(HttpMethod.class);
        for (Route route : routes) {
            Optional<Map<String, String>> parameters = route.template().match(segments.get());
            if (parameters.isPresent()) {
                if (route.method() == routeMethod) {
                    return new RouteMatch.Found(route, Collections.unmodifiableMap(parameters.get()));
                }
                allowed.add(route.method());
            }
        }
        return allowed.isEmpty() ? new RouteMatch.NotFound() : new RouteMatch.MethodNotAllowed(List.copyOf(allowed));
    }

    /**
     * Splits a raw path into percent-decoded segments; empty when the path is malformed.
     */
    private static Optional<List<String>> decode(String rawPath) {
        if (rawPath == null || !rawPath.startsWith("/")) {
            return Optional.empty();
        }
        List<String> segments = new ArrayList<>();
        if (rawPath.equals("/")) {
            return Optional.of(segments);
        }
        for (String segment : rawPath.substring(1).split("/", -1)) {
            Optional<String> decoded = percentDecode(segment);
            if (decoded.isEmpty()) {
                return Optional.empty();
            }
            segments.add(decoded.get());
        }
        return Optional.of(segments);
    }

    private static Optional<String> percentDecode(String segment) {
        if (segment.indexOf('%') < 0) {
            return Optional.of(segment);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int index = 0; index < segment.length(); index++) {
            char character = segment.charAt(index);
            if (character != '%') {
                bytes.writeBytes(String.valueOf(character).getBytes(StandardCharsets.UTF_8));
                continue;
            }
            if (index + 2 >= segment.length()) {
                return Optional.empty();
            }
            int high = Character.digit(segment.charAt(index + 1), 16);
            int low = Character.digit(segment.charAt(index + 2), 16);
            if (high < 0 || low < 0) {
                return Optional.empty();
            }
            bytes.write(high * 16 + low);
            index += 2;
        }
        return Optional.of(bytes.toString(StandardCharsets.UTF_8));
    }
}
