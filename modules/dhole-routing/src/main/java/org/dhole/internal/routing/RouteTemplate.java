package org.dhole.internal.routing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.dhole.routing.RoutingException;

/**
 * A parsed path template such as {@code /users/{id}}: a list of literal and parameter segments.
 *
 * <p>Rules: starts with {@code /}; {@code /} alone is the root; no empty segments (so no trailing
 * or double slash); a parameter {@code {name}} is a whole segment with a Java-identifier name;
 * parameter names are unique; no {@code ?}, {@code #} or stray braces.
 */
final class RouteTemplate {

    private static final Pattern PARAMETER = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)}");

    private final String text;
    private final List<String> segments;
    private final List<Boolean> parameters;

    private RouteTemplate(String text, List<String> segments, List<Boolean> parameters) {
        this.text = text;
        this.segments = List.copyOf(segments);
        this.parameters = List.copyOf(parameters);
    }

    static RouteTemplate parse(String text) {
        if (text == null || !text.startsWith("/")) {
            throw malformed(text, "a path must start with '/'");
        }
        if (text.contains("?") || text.contains("#")) {
            throw malformed(text, "a path must not contain '?' or '#'");
        }
        List<String> segments = new ArrayList<>();
        List<Boolean> parameters = new ArrayList<>();
        Set<String> names = new HashSet<>();
        if (!text.equals("/")) {
            for (String segment : text.substring(1).split("/", -1)) {
                if (segment.isEmpty()) {
                    throw malformed(text, "empty path segment (double or trailing '/')");
                }
                var matcher = PARAMETER.matcher(segment);
                if (matcher.matches()) {
                    if (!names.add(matcher.group(1))) {
                        throw malformed(text, "duplicate path parameter {" + matcher.group(1) + "}");
                    }
                    segments.add(matcher.group(1));
                    parameters.add(true);
                } else if (segment.contains("{") || segment.contains("}")) {
                    throw malformed(text, "a path parameter must be a whole segment such as {id}");
                } else {
                    segments.add(segment);
                    parameters.add(false);
                }
            }
        }
        return new RouteTemplate(text, segments, parameters);
    }

    /**
     * Joins a group prefix and a route path: {@code /api} + {@code /users} is {@code /api/users};
     * {@code /api} + {@code /} is {@code /api}.
     */
    static String join(String prefix, String path) {
        parse(path);
        if (prefix.isEmpty() || prefix.equals("/")) {
            return path;
        }
        return path.equals("/") ? prefix : prefix + path;
    }

    String text() {
        return text;
    }

    /**
     * Returns the template with parameter names removed, for example {@code /users/{}}: two
     * templates with the same shape match exactly the same paths.
     */
    String shape() {
        StringBuilder shape = new StringBuilder();
        for (int index = 0; index < segments.size(); index++) {
            shape.append('/').append(parameters.get(index) ? "{}" : segments.get(index));
        }
        return shape.isEmpty() ? "/" : shape.toString();
    }

    /**
     * Returns the path parameters when the decoded path segments match this template.
     */
    Optional<Map<String, String>> match(List<String> path) {
        if (path.size() != segments.size()) {
            return Optional.empty();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < segments.size(); index++) {
            if (parameters.get(index)) {
                values.put(segments.get(index), path.get(index));
            } else if (!segments.get(index).equals(path.get(index))) {
                return Optional.empty();
            }
        }
        return Optional.of(values);
    }

    /**
     * Total order used for matching: by segment count, then from most to least specific (at the
     * first differing position a literal segment wins over a parameter), then by shape. Only
     * templates of equal length can match the same path.
     */
    static int bySpecificity(RouteTemplate first, RouteTemplate second) {
        if (first.segments.size() != second.segments.size()) {
            return Integer.compare(first.segments.size(), second.segments.size());
        }
        for (int index = 0; index < first.segments.size(); index++) {
            boolean firstParameter = first.parameters.get(index);
            boolean secondParameter = second.parameters.get(index);
            if (firstParameter != secondParameter) {
                return firstParameter ? 1 : -1;
            }
        }
        return first.shape().compareTo(second.shape());
    }

    private static RoutingException malformed(String text, String problem) {
        return new RoutingException("Routing Error\n\nInvalid route path:\n" + text + "\n\nProblem:\n" + problem);
    }
}
