package org.dhole.internal.devtools;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Detects changes in watched directories and files by comparing snapshots (path, size and
 * modification time). Polling is portable and deterministic, and small projects make it cheap.
 */
final class ChangeWatcher {

    private final List<Path> directories;
    private final List<Path> files;
    private Map<Path, String> snapshot;

    ChangeWatcher(List<Path> directories, List<Path> files) {
        this.directories = List.copyOf(directories);
        this.files = List.copyOf(files);
        this.snapshot = snapshot();
    }

    /**
     * Returns the paths added, removed or modified since the previous call (or creation), sorted.
     */
    List<Path> changes() {
        Map<Path, String> current = snapshot();
        Set<Path> changed = new TreeSet<>();
        for (Map.Entry<Path, String> entry : current.entrySet()) {
            if (!Objects.equals(snapshot.get(entry.getKey()), entry.getValue())) {
                changed.add(entry.getKey());
            }
        }
        for (Path previous : snapshot.keySet()) {
            if (!current.containsKey(previous)) {
                changed.add(previous);
            }
        }
        snapshot = current;
        return new ArrayList<>(changed);
    }

    private Map<Path, String> snapshot() {
        Map<Path, String> state = new TreeMap<>();
        for (Path directory : directories) {
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(directory)) {
                walk.forEach(path -> record(path, state));
            } catch (IOException | UncheckedIOException e) {
                // A file vanished during the walk; the next snapshot sees the settled state.
            }
        }
        for (Path file : files) {
            record(file, state);
        }
        return state;
    }

    private static void record(Path path, Map<Path, String> state) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            if (attributes.isRegularFile()) {
                state.put(path, attributes.size() + ":" + attributes.lastModifiedTime().toMillis());
            }
        } catch (NoSuchFileException e) {
            // Absent files are simply not part of the snapshot.
        } catch (IOException e) {
            state.put(path, "unreadable");
        }
    }
}
