package org.dhole.internal.build;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import javax.lang.model.SourceVersion;

/**
 * The project manifest {@code dhole.toml} (BUILD_SYSTEM.md §3): project identity, Java target,
 * Dhole version, official module dependencies and the application class. Project metadata only,
 * never runtime configuration.
 *
 * <pre>
 * [project]
 * name = "hello"
 * version = "0.1.0"
 * java = "21"
 *
 * [dhole]
 * version = "0.1.0"
 *
 * [dependencies]
 * web = "0.1.0"
 *
 * [build]
 * main = "hello.App"
 * </pre>
 */
public record ProjectManifest(String name, String version, int java, String dholeVersion,
        Map<String, String> dependencies, String main) {

    public static final String FILE = "dhole.toml";

    private static final Pattern PROJECT_NAME = Pattern.compile("[a-z][a-z0-9-]*");
    private static final Pattern VERSION = Pattern.compile("[0-9A-Za-z][0-9A-Za-z.+-]*");
    private static final Map<String, Set<String>> KEYS = Map.of(
            "project", Set.of("name", "version", "java"),
            "dhole", Set.of("version"),
            "dependencies", Set.of(),
            "external-dependencies", Set.of(),
            "repositories", Set.of(),
            "build", Set.of("main"));

    public ProjectManifest {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(dholeVersion, "dholeVersion");
        dependencies = Collections.unmodifiableMap(new TreeMap<>(dependencies));
        Objects.requireNonNull(main, "main");
    }

    /**
     * @throws BuildException if the manifest is missing or invalid
     */
    public static ProjectManifest read(Path projectDirectory) {
        Path file = projectDirectory.resolve(FILE);
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (NoSuchFileException e) {
            throw new BuildException("Manifest Error\n\nNo " + FILE + " in " + projectDirectory.toAbsolutePath().normalize()
                    + ".\n\nRun the command in a Dhole project directory, or create one with 'dhole new <name>'.");
        } catch (IOException e) {
            throw new BuildException("Manifest Error\n\nUnable to read " + FILE + ": " + e.getMessage(), e);
        }
    }

    /**
     * @throws BuildException if the manifest is invalid
     */
    public static ProjectManifest parse(String text) {
        Map<String, Map<String, String>> tables = Toml.parse(text, FILE);
        for (Map.Entry<String, Map<String, String>> table : tables.entrySet()) {
            Set<String> allowed = KEYS.get(table.getKey());
            if (allowed == null) {
                throw invalid("unknown table [" + table.getKey() + "]; supported tables are [project], [dhole], "
                        + "[dependencies] and [build]");
            }
            if (!table.getKey().equals("dependencies") && !table.getKey().equals("external-dependencies")
                    && !table.getKey().equals("repositories")) {
                for (String key : table.getValue().keySet()) {
                    if (!allowed.contains(key)) {
                        throw invalid("unknown key '" + key + "' in [" + table.getKey() + "]");
                    }
                }
            }
        }
        if (!tables.getOrDefault("external-dependencies", Map.of()).isEmpty()) {
            throw invalid("[external-dependencies] is not supported by this Dhole version; only the modules "
                    + "bundled with Dhole can be used");
        }
        if (tables.containsKey("repositories")) {
            throw invalid("[repositories] is not supported by this Dhole version; dependencies come from the "
                    + "installed Dhole distribution");
        }
        String name = required(tables, "project", "name");
        if (!PROJECT_NAME.matcher(name).matches()) {
            throw invalid("project name '" + name + "' must start with a lower-case letter and contain only "
                    + "lower-case letters, digits and '-'");
        }
        String version = required(tables, "project", "version");
        requireVersion(version, "[project] version");
        String java = required(tables, "project", "java");
        if (!java.matches("[0-9]+")) {
            throw invalid("[project] java must be a Java feature version such as \"21\"");
        }
        int javaVersion = Integer.parseInt(java);
        if (javaVersion < 21) {
            throw invalid("[project] java = \"" + java + "\" is not supported; Dhole requires Java 21 or newer");
        }
        String dholeVersion = required(tables, "dhole", "version");
        requireVersion(dholeVersion, "[dhole] version");
        Map<String, String> dependencies = new LinkedHashMap<>(tables.getOrDefault("dependencies", Map.of()));
        dependencies.forEach((module, moduleVersion) -> requireVersion(moduleVersion, "dependency " + module));
        String main = required(tables, "build", "main");
        if (!SourceVersion.isName(main) || !main.contains(".")) {
            throw invalid("[build] main = \"" + main + "\" must be a fully qualified class name such as \"hello.App\"");
        }
        return new ProjectManifest(name, version, javaVersion, dholeVersion, dependencies, main);
    }

    private static String required(Map<String, Map<String, String>> tables, String table, String key) {
        String value = tables.getOrDefault(table, Map.of()).get(key);
        if (value == null || value.isBlank()) {
            throw invalid("missing [" + table + "] " + key);
        }
        return value;
    }

    private static void requireVersion(String version, String what) {
        if (!VERSION.matcher(version).matches()) {
            throw invalid(what + " has the invalid version \"" + version + "\"");
        }
    }

    private static BuildException invalid(String problem) {
        return new BuildException("Manifest Error\n\n" + FILE + ": " + problem + ".");
    }
}
