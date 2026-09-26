package org.dhole.internal.build;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The installed Dhole distribution: its version and the artifacts an application may use, read from
 * {@code lib/dhole-distribution.idx}. Written by the Dhole release build; the only source of
 * resolvable artifacts in this version (BUILD_SYSTEM.md §21).
 *
 * <pre>
 * dhole-distribution 1
 * dhole 0.1.0
 * test-roots org.junit.jupiter:junit-jupiter:6.1.3
 *
 * artifact org.dhole:dhole-web:0.1.0
 * file dhole-web-0.1.0.jar
 * requires org.dhole:dhole-routing:0.1.0 ...
 * </pre>
 */
public final class Distribution {

    public static final String CATALOG = "dhole-distribution.idx";
    private static final String HEADER = "dhole-distribution 1";

    private final Path lib;
    private final String version;
    private final List<Coordinates> testRoots;
    private final Map<Coordinates, Artifact> artifacts;

    private Distribution(Path lib, String version, List<Coordinates> testRoots, Map<Coordinates, Artifact> artifacts) {
        this.lib = lib;
        this.version = version;
        this.testRoots = List.copyOf(testRoots);
        this.artifacts = artifacts;
    }

    /**
     * @param home the installation directory containing {@code lib/}
     * @throws BuildException if the catalog is missing or malformed
     */
    public static Distribution load(Path home) {
        Path lib = home.resolve("lib");
        try {
            return parse(lib, Files.readString(lib.resolve(CATALOG), StandardCharsets.UTF_8));
        } catch (NoSuchFileException e) {
            throw new BuildException("Installation Error\n\nThe Dhole installation at " + home + " has no lib/" + CATALOG
                    + ".\n\nReinstall Dhole.");
        } catch (IOException e) {
            throw new BuildException("Installation Error\n\nUnable to read lib/" + CATALOG + ": " + e.getMessage(), e);
        }
    }

    static Distribution parse(Path lib, String text) {
        List<String> lines = text.lines().toList();
        if (lines.size() < 3 || !lines.get(0).equals(HEADER) || !lines.get(1).startsWith("dhole ")
                || !lines.get(2).startsWith("test-roots")) {
            throw malformed("expected '" + HEADER + "', 'dhole <version>' and 'test-roots'");
        }
        String version = lines.get(1).substring("dhole ".length());
        List<Coordinates> testRoots = coordinates(lines.get(2).substring("test-roots".length()).strip());
        Map<Coordinates, Artifact> artifacts = new TreeMap<>();
        Coordinates current = null;
        String file = null;
        List<Coordinates> requires = List.of();
        for (int index = 3; index <= lines.size(); index++) {
            String line = index < lines.size() ? lines.get(index) : "";
            if (line.isEmpty()) {
                if (current != null) {
                    if (file == null) {
                        throw malformed("artifact " + current + " has no file");
                    }
                    artifacts.put(current, new Artifact(current, file, requires));
                    current = null;
                    file = null;
                    requires = List.of();
                }
            } else if (line.startsWith("artifact ")) {
                current = coordinate(line.substring("artifact ".length()));
            } else if (current != null && line.startsWith("file ")) {
                file = line.substring("file ".length());
                if (file.contains("/") || file.contains("\\") || !file.endsWith(".jar")) {
                    throw malformed("invalid file " + file);
                }
            } else if (current != null && line.startsWith("requires ")) {
                requires = coordinates(line.substring("requires ".length()));
            } else {
                throw malformed("unexpected line " + (index + 1));
            }
        }
        return new Distribution(lib, version, testRoots, artifacts);
    }

    public String version() {
        return version;
    }

    public List<Coordinates> testRoots() {
        return testRoots;
    }

    public Optional<Artifact> artifact(Coordinates coordinates) {
        return Optional.ofNullable(artifacts.get(coordinates));
    }

    public Collection<Artifact> artifacts() {
        return artifacts.values();
    }

    /**
     * Returns where the artifact's file is installed; it may be missing.
     */
    public Path file(Artifact artifact) {
        return lib.resolve(artifact.file());
    }

    public Path lib() {
        return lib;
    }

    private static List<Coordinates> coordinates(String text) {
        List<Coordinates> list = new ArrayList<>();
        if (!text.isEmpty()) {
            for (String part : text.split(" ")) {
                list.add(coordinate(part));
            }
        }
        return list;
    }

    private static Coordinates coordinate(String text) {
        try {
            return Coordinates.parse(text);
        } catch (IllegalArgumentException e) {
            throw malformed(e.getMessage());
        }
    }

    private static BuildException malformed(String problem) {
        return new BuildException("Installation Error\n\nMalformed lib/" + CATALOG + ": " + problem + ".\n\nReinstall Dhole.");
    }

    /**
     * One installed artifact with the artifacts it needs at runtime.
     */
    public record Artifact(Coordinates coordinates, String file, List<Coordinates> requires) {

        public Artifact {
            Objects.requireNonNull(coordinates, "coordinates");
            Objects.requireNonNull(file, "file");
            requires = List.copyOf(requires);
        }
    }
}
