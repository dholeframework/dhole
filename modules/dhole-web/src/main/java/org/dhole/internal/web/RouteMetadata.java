package org.dhole.internal.web;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dhole.http.HttpMethod;
import org.dhole.routing.RoutingException;

/**
 * Typed route metadata read from {@code META-INF/dhole/routes.idx}, format version 1
 * (METADATA_COMPILER.md §34.3). Written by the metadata compiler; this reader has no dependency on
 * it. Strict: an unknown version fails with a compatibility error, any malformed line with its
 * line number. Types are kept as text and resolved when a binding plan is built.
 */
final class RouteMetadata {

    static final String LOCATION = "META-INF/dhole/routes.idx";
    static final int VERSION = 1;

    private static final Pattern HEADER = Pattern.compile("dhole-routes (\\S+)");
    private static final Pattern ROUTE = Pattern.compile("route (\\S+) (GET|POST|PUT|PATCH|DELETE) (/\\S*)");
    private static final Pattern PARAMETER = Pattern.compile(
            "parameter ([A-Za-z_$][A-Za-z0-9_$]*) (PATH|QUERY|HEADER|BODY|REQUEST) (\\S+)");
    private static final Pattern SINGLE = Pattern.compile("(handler|response|source) (\\S+)");

    private final Map<String, Entry> routes;

    private RouteMetadata(Map<String, Entry> routes) {
        this.routes = Collections.unmodifiableMap(routes);
    }

    static RouteMetadata empty() {
        return new RouteMetadata(Map.of());
    }

    /**
     * Reads the application's route index from {@code loader}; empty when there is none.
     *
     * @throws RoutingException if there are several indexes, or the index is incompatible or malformed
     */
    static RouteMetadata load(ClassLoader loader) {
        Objects.requireNonNull(loader, "loader");
        List<URL> resources;
        try {
            resources = Collections.list(loader.getResources(LOCATION));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read " + LOCATION, e);
        }
        if (resources.isEmpty()) {
            return empty();
        }
        if (resources.size() > 1) {
            throw new RoutingException("Metadata Error\n\nFound " + resources.size() + " " + LOCATION
                    + " files; an application has exactly one route index.");
        }
        try (InputStream input = resources.get(0).openStream()) {
            return parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read " + LOCATION, e);
        }
    }

    static RouteMetadata parse(String text) {
        String[] lines = text.split("\n", -1);
        Matcher header = HEADER.matcher(lines[0]);
        if (!header.matches()) {
            throw malformed(1, "expected header 'dhole-routes " + VERSION + "'");
        }
        if (!header.group(1).equals(Integer.toString(VERSION))) {
            throw new RoutingException("Metadata Compatibility Error\n\nApplication route metadata version: "
                    + header.group(1) + "\nRuntime supports: " + VERSION
                    + "\n\nRebuild the application using a compatible Dhole build tool.");
        }
        Map<String, Entry> routes = new TreeMap<>();
        int index = 1;
        while (index < lines.length) {
            if (lines[index].isEmpty()) {
                index++;
                continue;
            }
            Matcher route = ROUTE.matcher(lines[index]);
            if (!route.matches()) {
                throw malformed(index + 1, "expected 'route <controller> <METHOD> <path>'");
            }
            int routeLine = index + 1;
            index++;
            String handler = single(lines, index++, "handler");
            List<Parameter> parameters = new ArrayList<>();
            while (index < lines.length && lines[index].startsWith("parameter ")) {
                Matcher parameter = PARAMETER.matcher(lines[index]);
                if (!parameter.matches()) {
                    throw malformed(index + 1, "expected 'parameter <name> <SOURCE> <type>'");
                }
                parameters.add(new Parameter(parameter.group(1), ParameterSource.valueOf(parameter.group(2)),
                        parameter.group(3)));
                index++;
            }
            String response = single(lines, index++, "response");
            Optional<String> source = Optional.empty();
            if (index < lines.length && lines[index].startsWith("source ")) {
                source = Optional.of(single(lines, index++, "source"));
            }
            if (index < lines.length && !lines[index].isEmpty()) {
                throw malformed(index + 1, "unexpected line '" + lines[index] + "'");
            }
            Entry entry = new Entry(route.group(1), HttpMethod.valueOf(route.group(2)), route.group(3), handler,
                    List.copyOf(parameters), response, source);
            if (routes.putIfAbsent(key(entry.controller(), entry.method(), entry.path()), entry) != null) {
                throw malformed(routeLine, "duplicate route " + entry.method() + " " + entry.path());
            }
        }
        return new RouteMetadata(routes);
    }

    /**
     * Returns the metadata of a typed route by its stable identity.
     */
    Optional<Entry> route(String controller, HttpMethod method, String path) {
        return Optional.ofNullable(routes.get(key(controller, method, path)));
    }

    private static String single(String[] lines, int index, String keyword) {
        if (index >= lines.length) {
            throw malformed(index + 1, "expected '" + keyword + " <value>'");
        }
        Matcher matcher = SINGLE.matcher(lines[index]);
        if (!matcher.matches() || !matcher.group(1).equals(keyword)) {
            throw malformed(index + 1, "expected '" + keyword + " <value>'");
        }
        return matcher.group(2);
    }

    private static String key(String controller, HttpMethod method, String path) {
        return controller + " " + method + " " + path;
    }

    private static RoutingException malformed(int line, String problem) {
        return new RoutingException("Metadata Error\n\nMalformed " + LOCATION + " at line " + line + ":\n" + problem
                + "\n\nRebuild the application.");
    }

    record Entry(String controller, HttpMethod method, String path, String handler, List<Parameter> parameters,
            String response, Optional<String> source) {
    }

    record Parameter(String name, ParameterSource source, String type) {
    }
}
