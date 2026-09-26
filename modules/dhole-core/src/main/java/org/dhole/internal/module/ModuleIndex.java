package org.dhole.internal.module;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The modules of an application, read from {@code META-INF/dhole/modules.idx} (MODULE_SYSTEM.md
 * §29). The same grammar, with the header {@code dhole-module 1} and exactly one block, is the
 * descriptor each official module ships in its own JAR.
 *
 * <pre>
 * dhole-modules 1
 *
 * module web
 * activator org.dhole.internal.web.WebActivator
 * requires http routing
 * </pre>
 */
public final class ModuleIndex {

    public static final String LOCATION = "META-INF/dhole/modules.idx";
    public static final String DESCRIPTOR_LOCATION = "META-INF/dhole/module.idx";

    private static final String HEADER = "dhole-modules 1";
    private static final String DESCRIPTOR_HEADER = "dhole-module 1";
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9-]*");
    private static final Pattern BINARY_NAME =
            Pattern.compile("[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*"
                    + "(\\.[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*)*");

    private final Map<String, ModuleDescriptor> modules;

    private ModuleIndex(Map<String, ModuleDescriptor> modules) {
        this.modules = modules;
    }

    public static ModuleIndex empty() {
        return new ModuleIndex(Map.of());
    }

    /**
     * Reads the application's index; an application without one has no modules.
     */
    public static ModuleIndex load(ClassLoader loader) {
        URL resource = loader.getResource(LOCATION);
        if (resource == null) {
            return empty();
        }
        try (InputStream input = resource.openStream()) {
            return parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read " + LOCATION, e);
        }
    }

    /**
     * @throws IllegalStateException if the text is not a {@code dhole-modules 1} index
     */
    public static ModuleIndex parse(String text) {
        return new ModuleIndex(blocks(text, HEADER, LOCATION));
    }

    /**
     * Parses the descriptor of one module.
     *
     * @throws IllegalStateException if the text is not a {@code dhole-module 1} descriptor with
     *         exactly one module
     */
    public static ModuleDescriptor parseDescriptor(String text) {
        Map<String, ModuleDescriptor> blocks = blocks(text, DESCRIPTOR_HEADER, DESCRIPTOR_LOCATION);
        if (blocks.size() != 1) {
            throw malformed(DESCRIPTOR_LOCATION, 1, "a descriptor declares exactly one module");
        }
        return blocks.values().iterator().next();
    }

    /**
     * Writes an index: blocks sorted by ID, requirements sorted.
     */
    public static String format(Collection<ModuleDescriptor> modules) {
        StringBuilder text = new StringBuilder(HEADER).append('\n');
        modules.stream().sorted(Comparator.comparing(ModuleDescriptor::id)).forEach(module -> {
            text.append("\nmodule ").append(module.id()).append('\n');
            module.activator().ifPresent(activator -> text.append("activator ").append(activator).append('\n'));
            if (!module.requires().isEmpty()) {
                text.append("requires ").append(String.join(" ", module.requires().stream().sorted().toList()))
                        .append('\n');
            }
        });
        return text.toString();
    }

    public List<ModuleDescriptor> modules() {
        return List.copyOf(modules.values());
    }

    /**
     * Returns the modules in activation order: every module after the modules it requires, ties
     * broken by ID.
     *
     * @throws IllegalStateException if a required module is missing or requirements are circular
     */
    public List<ModuleDescriptor> activationOrder() {
        for (ModuleDescriptor module : modules.values()) {
            for (String required : module.requires()) {
                if (!modules.containsKey(required)) {
                    throw new IllegalStateException("Module Dependency Error\n\nModule '" + module.id()
                            + "' requires module '" + required + "', which is not part of the application."
                            + "\n\nRebuild the application.");
                }
            }
        }
        List<ModuleDescriptor> order = new ArrayList<>();
        Set<String> done = new HashSet<>();
        for (String id : modules.keySet()) {
            visit(id, new ArrayList<>(), done, order);
        }
        return order;
    }

    private void visit(String id, List<String> path, Set<String> done, List<ModuleDescriptor> order) {
        if (done.contains(id)) {
            return;
        }
        if (path.contains(id)) {
            List<String> cycle = new ArrayList<>(path.subList(path.indexOf(id), path.size()));
            cycle.add(id);
            StringBuilder message = new StringBuilder("Module Dependency Error\n\nCircular dependency detected:\n");
            for (int index = 0; index < cycle.size(); index++) {
                message.append(index == 0 ? "" : "  ".repeat(2 * index - 1) + "-> ").append(cycle.get(index)).append('\n');
            }
            throw new IllegalStateException(message.toString().stripTrailing());
        }
        path.add(id);
        ModuleDescriptor module = modules.get(id);
        for (String required : module.requires().stream().sorted().toList()) {
            visit(required, path, done, order);
        }
        path.remove(path.size() - 1);
        done.add(id);
        order.add(module);
    }

    private static Map<String, ModuleDescriptor> blocks(String text, String header, String location) {
        Objects.requireNonNull(text, "text");
        List<String> lines = text.lines().toList();
        if (lines.isEmpty() || !lines.get(0).startsWith(header.substring(0, header.indexOf(' ') + 1))) {
            throw malformed(location, 1, "expected '" + header + "'");
        }
        if (!lines.get(0).equals(header)) {
            throw new IllegalStateException("Metadata Compatibility Error\n\n" + location + " has format '"
                    + lines.get(0) + "'; this Dhole version reads '" + header + "'.\n\nRebuild the application.");
        }
        if (lines.size() > 1 && !lines.get(1).isEmpty()) {
            throw malformed(location, 2, "expected an empty line after the header");
        }
        Map<String, ModuleDescriptor> modules = new TreeMap<>();
        String id = null;
        String activator = null;
        List<String> requires = null;
        int start = 0;
        for (int index = 1; index <= lines.size(); index++) {
            String line = index < lines.size() ? lines.get(index) : "";
            int number = index + 1;
            if (line.isEmpty()) {
                if (id != null) {
                    if (modules.put(id, new ModuleDescriptor(id, Optional.ofNullable(activator),
                            requires == null ? List.of() : requires)) != null) {
                        throw malformed(location, start, "module '" + id + "' is declared more than once");
                    }
                    id = null;
                    activator = null;
                    requires = null;
                }
                continue;
            }
            int space = line.indexOf(' ');
            String keyword = space < 0 ? line : line.substring(0, space);
            String value = space < 0 ? "" : line.substring(space + 1);
            if (keyword.equals("module")) {
                if (id != null) {
                    throw malformed(location, number, "missing empty line before 'module'");
                }
                if (!ID.matcher(value).matches()) {
                    throw malformed(location, number, "invalid module ID '" + value + "'");
                }
                id = value;
                start = number;
            } else if (id == null) {
                throw malformed(location, number, "'" + keyword + "' outside a module block");
            } else if (keyword.equals("activator") && activator == null && requires == null) {
                if (!BINARY_NAME.matcher(value).matches()) {
                    throw malformed(location, number, "invalid activator class '" + value + "'");
                }
                activator = value;
            } else if (keyword.equals("requires") && requires == null) {
                requires = List.of(value.split(" ", -1));
                Set<String> unique = new HashSet<>();
                for (String required : requires) {
                    if (!ID.matcher(required).matches() || !unique.add(required) || required.equals(id)) {
                        throw malformed(location, number, "invalid requirement '" + required + "'");
                    }
                }
            } else {
                throw malformed(location, number, "unexpected '" + keyword + "'");
            }
        }
        return new LinkedHashMap<>(modules);
    }

    private static IllegalStateException malformed(String location, int line, String problem) {
        return new IllegalStateException("Metadata Error\n\nMalformed " + location + " at line " + line + ": "
                + problem + ".\n\nRebuild the application.");
    }
}
