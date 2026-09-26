package org.dhole.internal.di;

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
import java.util.regex.Pattern;

import org.dhole.di.DependencyException;

/**
 * Build-time component metadata read from {@code META-INF/dhole/components.idx}, format version 1
 * (METADATA_COMPILER.md §34.2). The index is written by the metadata compiler; this reader has no
 * dependency on it.
 *
 * <p>Reading is strict: an unknown version fails with a compatibility error and any malformed line
 * fails with its line number. Classes named by the index are loaded only when a resolution needs
 * them; the index is never used to scan or instantiate types eagerly.
 */
final class ComponentMetadata {

    static final String LOCATION = "META-INF/dhole/components.idx";
    static final int VERSION = 1;

    private static final Pattern HEADER = Pattern.compile("dhole-metadata (\\S+)");
    private static final Map<String, Class<?>> PRIMITIVES = Map.of(
            "boolean", boolean.class, "byte", byte.class, "char", char.class, "short", short.class,
            "int", int.class, "long", long.class, "float", float.class, "double", double.class);

    private static final ComponentMetadata EMPTY =
            new ComponentMetadata(Map.of(), ComponentMetadata.class.getClassLoader());

    private final Map<String, TypeMetadata> types;
    private final ClassLoader loader;

    private ComponentMetadata(Map<String, TypeMetadata> types, ClassLoader loader) {
        this.types = Collections.unmodifiableMap(new TreeMap<>(types));
        this.loader = loader;
    }

    static ComponentMetadata empty() {
        return EMPTY;
    }

    /**
     * Reads the application's component index from {@code loader}; empty when there is none.
     *
     * @throws DependencyException if there are several indexes, or the index is incompatible or
     *         malformed
     */
    static ComponentMetadata load(ClassLoader loader) {
        Objects.requireNonNull(loader, "loader");
        List<URL> resources;
        try {
            resources = Collections.list(loader.getResources(LOCATION));
        } catch (IOException e) {
            throw new DependencyException("Metadata Error\n\nUnable to read " + LOCATION + ".", e);
        }
        if (resources.isEmpty()) {
            return new ComponentMetadata(Map.of(), loader);
        }
        if (resources.size() > 1) {
            throw new DependencyException("Metadata Error\n\nFound " + resources.size() + " " + LOCATION
                    + " files; an application has exactly one component index.");
        }
        try (InputStream input = resources.get(0).openStream()) {
            return parse(new String(input.readAllBytes(), StandardCharsets.UTF_8), loader);
        } catch (IOException e) {
            throw new DependencyException("Metadata Error\n\nUnable to read " + LOCATION + ".", e);
        }
    }

    static ComponentMetadata parse(String text, ClassLoader loader) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(loader, "loader");
        return new ComponentMetadata(new Parser(text.split("\n", -1)).parse(), loader);
    }

    Optional<TypeMetadata> type(String binaryName) {
        return Optional.ofNullable(types.get(binaryName));
    }

    /**
     * Returns the indexed classes that have {@code supertype} among their supertypes, sorted by name.
     */
    List<String> providersOf(String supertype) {
        return types.values().stream()
                .filter(type -> type.supertypes().contains(supertype))
                .map(TypeMetadata::type)
                .toList();
    }

    /**
     * Loads a class named in {@code Class.forName} format.
     *
     * @throws DependencyException if the class does not exist
     */
    Class<?> loadClass(String name) {
        Class<?> primitive = PRIMITIVES.get(name);
        if (primitive != null) {
            return primitive;
        }
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            throw new DependencyException("Metadata Error\n\nThe component index names " + name
                    + ", which cannot be loaded.\n\nRebuild the application.", e);
        }
    }

    record TypeMetadata(String type, Optional<List<String>> constructor, Optional<Unusable> unusable,
            List<String> supertypes, Optional<String> source) {
    }

    /**
     * @param argument the constructor count or the parameter index, when the reason has one
     */
    record Unusable(String reason, int argument) {
    }

    private static final class Parser {

        private final String[] lines;
        private int index;

        Parser(String[] lines) {
            this.lines = lines;
        }

        Map<String, TypeMetadata> parse() {
            Map<String, TypeMetadata> types = new TreeMap<>();
            readHeader();
            while (index < lines.length) {
                if (lines[index].isEmpty()) {
                    index++;
                    continue;
                }
                TypeMetadata type = readBlock();
                if (types.putIfAbsent(type.type(), type) != null) {
                    throw malformed("duplicate component " + type.type());
                }
            }
            return types;
        }

        private void readHeader() {
            var matcher = HEADER.matcher(lines[0]);
            if (!matcher.matches()) {
                throw malformed("expected header 'dhole-metadata " + VERSION + "'");
            }
            if (!matcher.group(1).equals(Integer.toString(VERSION))) {
                throw new DependencyException("Metadata Compatibility Error\n\nApplication metadata version: "
                        + matcher.group(1) + "\nRuntime supports: " + VERSION
                        + "\n\nRebuild the application using a compatible Dhole build tool.");
            }
            index = 1;
        }

        private TypeMetadata readBlock() {
            String type = value("component");
            if (type == null) {
                throw malformed("expected 'component <type>'");
            }
            requireSingle(type);
            index++;
            Optional<List<String>> constructor = Optional.empty();
            if (keyword("constructor")) {
                String arguments = lines[index].substring("constructor".length()).strip();
                constructor = Optional.of(arguments.isEmpty() ? List.of() : List.of(arguments.split(" ")));
                index++;
            }
            Optional<Unusable> unusable = Optional.empty();
            if (keyword("unusable")) {
                unusable = Optional.of(unusable(value("unusable"), constructor));
                index++;
            }
            if (constructor.isEmpty() && unusable.isEmpty()) {
                throw malformed("component " + type + " has neither 'constructor' nor 'unusable'");
            }
            List<String> supertypes = new ArrayList<>();
            while (keyword("supertype")) {
                String supertype = value("supertype");
                requireSingle(supertype);
                supertypes.add(supertype);
                index++;
            }
            Optional<String> source = Optional.empty();
            if (keyword("source")) {
                source = Optional.of(value("source"));
                index++;
            }
            if (index < lines.length && !lines[index].isEmpty()) {
                throw malformed("unexpected line '" + lines[index] + "'");
            }
            return new TypeMetadata(type, constructor, unusable, List.copyOf(supertypes), source);
        }

        private Unusable unusable(String text, Optional<List<String>> constructor) {
            String[] parts = text == null ? new String[0] : text.split(" ");
            if (parts.length == 1 && List.of("not-public", "not-static-nested", "no-public-constructor").contains(parts[0])
                    && constructor.isEmpty()) {
                return new Unusable(parts[0], 0);
            }
            if (parts.length == 2 && parts[0].equals("multiple-public-constructors") && constructor.isEmpty()) {
                int count = number(parts[1]);
                if (count > 1) {
                    return new Unusable(parts[0], count);
                }
            }
            if (parts.length == 2 && parts[0].equals("parameterized-dependency") && constructor.isPresent()) {
                int parameter = number(parts[1]);
                if (parameter >= 0 && parameter < constructor.get().size()) {
                    return new Unusable(parts[0], parameter);
                }
            }
            throw malformed("invalid 'unusable' record");
        }

        private int number(String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        private boolean keyword(String keyword) {
            return index < lines.length
                    && (lines[index].equals(keyword) || lines[index].startsWith(keyword + " "));
        }

        private String value(String keyword) {
            if (index >= lines.length || !lines[index].startsWith(keyword + " ")) {
                return null;
            }
            String value = lines[index].substring(keyword.length() + 1);
            if (value.isBlank()) {
                throw malformed("'" + keyword + "' requires a value");
            }
            return value;
        }

        private void requireSingle(String value) {
            if (value.contains(" ")) {
                throw malformed("unexpected space in '" + value + "'");
            }
        }

        private DependencyException malformed(String problem) {
            return new DependencyException("Metadata Error\n\nMalformed " + LOCATION + " at line "
                    + (index + 1) + ":\n" + problem + "\n\nRebuild the application.");
        }
    }
}
