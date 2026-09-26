package org.dhole.internal.devtools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChangeWatcherTest {

    @TempDir
    Path root;

    @Test
    void reportsAddedModifiedAndRemovedFilesOnce() throws IOException {
        Path sources = Files.createDirectories(root.resolve("src/main/java/hello"));
        Path app = Files.writeString(sources.resolve("App.java"), "class App {}");
        Path manifest = root.resolve("dhole.toml");
        ChangeWatcher watcher = new ChangeWatcher(List.of(root.resolve("src/main/java")), List.of(manifest, root.resolve(".env")));

        assertEquals(List.of(), watcher.changes());

        Files.writeString(app, "class App { int changed; }");
        Files.setLastModifiedTime(app, FileTime.from(Instant.now().plusSeconds(5)));
        Path added = Files.writeString(Files.createDirectories(sources.resolve("controllers")).resolve("Hello.java"), "x");
        Files.writeString(manifest, "[project]\n");

        assertEquals(List.of(manifest, app, added).stream().sorted().toList(), watcher.changes());
        assertEquals(List.of(), watcher.changes());

        Files.delete(added);
        assertEquals(List.of(added), watcher.changes());
    }

    @Test
    void missingDirectoriesAreWatchedOnceTheyAppear() throws IOException {
        ChangeWatcher watcher = new ChangeWatcher(List.of(root.resolve("src/main/resources")), List.of());

        Path resource = Files.writeString(Files.createDirectories(root.resolve("src/main/resources")).resolve("a.txt"), "a");

        assertEquals(List.of(resource), watcher.changes());
    }
}
