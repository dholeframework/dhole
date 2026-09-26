package org.dhole.internal.build;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * {@code dhole.lock}, format version 1 (BUILD_SYSTEM.md §21): the exact artifacts selected for the
 * application with the SHA-256 of their bytes. Generated build state: committed, never hand-edited.
 *
 * <pre>
 * dhole-lock 1
 * dhole 0.1.0
 *
 * artifact org.dhole:dhole-core:0.1.0
 * scope runtime
 * sha256 3f1c...
 * </pre>
 *
 * Entries are sorted by scope (runtime, then test) and coordinates; the file contains no paths.
 */
public record LockFile(String dholeVersion, List<Entry> entries) {

    public static final String FILE = "dhole.lock";

    private static final String HEADER = "dhole-lock 1";
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Comparator<Entry> ORDER =
            Comparator.comparing(Entry::scope).thenComparing(Entry::coordinates);

    public LockFile {
        Objects.requireNonNull(dholeVersion, "dholeVersion");
        entries = entries.stream().sorted(ORDER).toList();
    }

    public String format() {
        StringBuilder text = new StringBuilder(HEADER).append('\n').append("dhole ").append(dholeVersion).append('\n');
        for (Entry entry : entries) {
            text.append("\nartifact ").append(entry.coordinates()).append('\n')
                    .append("scope ").append(entry.scope().name().toLowerCase(Locale.ROOT)).append('\n')
                    .append("sha256 ").append(entry.sha256()).append('\n');
        }
        return text.toString();
    }

    /**
     * @throws BuildException if the text is not a valid {@code dhole-lock 1} file
     */
    public static LockFile parse(String text) {
        List<String> lines = text.lines().toList();
        if (lines.isEmpty() || !lines.get(0).startsWith("dhole-lock ")) {
            throw malformed(1, "expected '" + HEADER + "'");
        }
        if (!lines.get(0).equals(HEADER)) {
            throw new BuildException("Lock Error\n\n" + FILE + " has format '" + lines.get(0) + "'; this Dhole version reads '"
                    + HEADER + "'.\n\nUse the Dhole version that wrote it, or regenerate it with 'dhole build --update-lock'.");
        }
        if (lines.size() < 2 || !lines.get(1).startsWith("dhole ") || lines.get(1).length() == "dhole ".length()) {
            throw malformed(2, "expected 'dhole <version>'");
        }
        List<Entry> entries = new ArrayList<>();
        int index = 2;
        while (index < lines.size()) {
            if (!lines.get(index).isEmpty()) {
                throw malformed(index + 1, "expected an empty line");
            }
            Coordinates coordinates = coordinates(value(lines, index + 1, "artifact"), index + 2);
            Scope scope = scope(value(lines, index + 2, "scope"), index + 3);
            String sha256 = value(lines, index + 3, "sha256");
            if (!SHA256.matcher(sha256).matches()) {
                throw malformed(index + 4, "invalid sha256");
            }
            entries.add(new Entry(coordinates, scope, sha256));
            index += 4;
        }
        if (entries.stream().map(Entry::coordinates).distinct().count() != entries.size()) {
            throw malformed(1, "an artifact is listed more than once");
        }
        return new LockFile(lines.get(1).substring("dhole ".length()), entries);
    }

    /**
     * Reads the lock of a project, if it has one.
     */
    public static Optional<LockFile> read(Path projectDirectory) {
        Path file = projectDirectory.resolve(FILE);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(parse(Files.readString(file, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new BuildException("Lock Error\n\nUnable to read " + FILE + ": " + e.getMessage(), e);
        }
    }

    public void write(Path projectDirectory) {
        try {
            Files.writeString(projectDirectory.resolve(FILE), format(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BuildException("Lock Error\n\nUnable to write " + FILE + ": " + e.getMessage(), e);
        }
    }

    /**
     * Returns the SHA-256 of a file's bytes as lower-case hex.
     */
    public static String sha256(Path file) {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            for (int read; (read = input.read(buffer)) > 0; ) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) {
            throw new BuildException("Lock Error\n\nUnable to read " + file.getFileName() + ": " + e.getMessage(), e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String value(List<String> lines, int index, String keyword) {
        if (index >= lines.size() || !lines.get(index).startsWith(keyword + " ")) {
            throw malformed(index + 1, "expected '" + keyword + "'");
        }
        return lines.get(index).substring(keyword.length() + 1);
    }

    private static Coordinates coordinates(String text, int line) {
        try {
            return Coordinates.parse(text);
        } catch (IllegalArgumentException e) {
            throw malformed(line, e.getMessage());
        }
    }

    private static Scope scope(String text, int line) {
        return switch (text) {
            case "runtime" -> Scope.RUNTIME;
            case "test" -> Scope.TEST;
            default -> throw malformed(line, "scope must be 'runtime' or 'test'");
        };
    }

    private static BuildException malformed(int line, String problem) {
        return new BuildException("Lock Error\n\nMalformed " + FILE + " at line " + line + ": " + problem
                + ".\n\nDo not edit " + FILE + " by hand; regenerate it with 'dhole build --update-lock'.");
    }

    /**
     * Where an artifact is used.
     */
    public enum Scope {
        RUNTIME,
        TEST
    }

    /**
     * One locked artifact.
     */
    public record Entry(Coordinates coordinates, Scope scope, String sha256) {

        public Entry {
            Objects.requireNonNull(coordinates, "coordinates");
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(sha256, "sha256");
        }
    }
}
