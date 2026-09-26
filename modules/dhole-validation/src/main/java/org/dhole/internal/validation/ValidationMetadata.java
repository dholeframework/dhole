package org.dhole.internal.validation;

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

/**
 * Validation metadata read from {@code META-INF/dhole/validation.idx}, format version 1
 * (METADATA_COMPILER.md §34.4): the ordered field names of each validated type. Written by the
 * metadata compiler; no dependency on it. Strict: unknown versions and malformed lines fail.
 *
 * <p>Public only for the internal web runtime; not application API.
 */
public final class ValidationMetadata {

    static final String LOCATION = "META-INF/dhole/validation.idx";
    static final int VERSION = 1;

    private static final Pattern HEADER = Pattern.compile("dhole-validation (\\S+)");
    private static final Pattern TYPE = Pattern.compile("type (\\S+)");
    private static final Pattern FIELD = Pattern.compile("field ([A-Za-z_$][A-Za-z0-9_$]*) (\\S+)");
    private static final Pattern SOURCE = Pattern.compile("source (\\S+)");

    private final Map<String, List<String>> fields;

    private ValidationMetadata(Map<String, List<String>> fields) {
        this.fields = Collections.unmodifiableMap(fields);
    }

    public static ValidationMetadata empty() {
        return new ValidationMetadata(Map.of());
    }

    /**
     * Reads the application's validation index from {@code loader}; empty when there is none.
     *
     * @throws IllegalStateException if there are several indexes, or the index is incompatible or
     *         malformed
     */
    public static ValidationMetadata load(ClassLoader loader) {
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
            throw new IllegalStateException("Metadata Error\n\nFound " + resources.size() + " " + LOCATION
                    + " files; an application has exactly one validation index.");
        }
        try (InputStream input = resources.get(0).openStream()) {
            return parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read " + LOCATION, e);
        }
    }

    public static ValidationMetadata parse(String text) {
        String[] lines = text.split("\n", -1);
        Matcher header = HEADER.matcher(lines[0]);
        if (!header.matches()) {
            throw malformed(1, "expected header 'dhole-validation " + VERSION + "'");
        }
        if (!header.group(1).equals(Integer.toString(VERSION))) {
            throw new IllegalStateException("Metadata Compatibility Error\n\nApplication validation metadata version: "
                    + header.group(1) + "\nRuntime supports: " + VERSION
                    + "\n\nRebuild the application using a compatible Dhole build tool.");
        }
        Map<String, List<String>> types = new TreeMap<>();
        int index = 1;
        while (index < lines.length) {
            if (lines[index].isEmpty()) {
                index++;
                continue;
            }
            Matcher type = TYPE.matcher(lines[index]);
            if (!type.matches()) {
                throw malformed(index + 1, "expected 'type <name>'");
            }
            int typeLine = index + 1;
            index++;
            List<String> names = new ArrayList<>();
            while (index < lines.length && lines[index].startsWith("field ")) {
                Matcher field = FIELD.matcher(lines[index]);
                if (!field.matches()) {
                    throw malformed(index + 1, "expected 'field <name> <type>'");
                }
                names.add(field.group(1));
                index++;
            }
            if (index < lines.length && SOURCE.matcher(lines[index]).matches()) {
                index++;
            }
            if (index < lines.length && !lines[index].isEmpty()) {
                throw malformed(index + 1, "unexpected line '" + lines[index] + "'");
            }
            if (types.putIfAbsent(type.group(1), List.copyOf(names)) != null) {
                throw malformed(typeLine, "duplicate type " + type.group(1));
            }
        }
        return new ValidationMetadata(types);
    }

    /**
     * Returns the ordered field names of a validated type, by binary name.
     */
    Optional<List<String>> fields(String type) {
        return Optional.ofNullable(fields.get(type));
    }

    private static IllegalStateException malformed(int line, String problem) {
        return new IllegalStateException("Metadata Error\n\nMalformed " + LOCATION + " at line " + line + ":\n"
                + problem + "\n\nRebuild the application.");
    }
}
