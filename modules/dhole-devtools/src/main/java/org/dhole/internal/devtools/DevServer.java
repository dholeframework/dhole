package org.dhole.internal.devtools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.dhole.internal.build.ApplicationBuild;
import org.dhole.internal.build.BuildException;
import org.dhole.internal.build.DependencyResolver;
import org.dhole.internal.build.Distribution;
import org.dhole.internal.build.LockFile;
import org.dhole.internal.build.ProjectLayout;
import org.dhole.internal.build.ProjectManifest;
import org.dhole.internal.build.ResolvedArtifacts;
import org.dhole.internal.build.Terminal;

/**
 * {@code dhole dev} (DEV_MODE.md, HOT_RELOAD.md level 1 — fast restart):
 *
 * <pre>
 * resolve -> compile + metadata -> start -> watch
 * change  -> compile + metadata -> stop old application -> fresh application -> ready
 * </pre>
 *
 * A change that does not compile is rejected and the previous version keeps running. A change to
 * {@code dhole.toml} resolves dependencies again; {@code .env} changes restart with the new
 * configuration. Commands on standard input: {@code r} restarts, {@code q} quits. Every source
 * change recompiles the whole source set (the metadata compiler indexes all types).
 */
public final class DevServer {

    private static final long POLL_MILLIS = 250;
    private static final long QUIET_MILLIS = 200;

    private final ProjectLayout layout;
    private final Distribution distribution;
    private final PrintStream output;
    private final InputStream input;
    private final BlockingQueue<String> commands = new LinkedBlockingQueue<>();
    private final ApplicationRuntime runtime;
    private ProjectManifest manifest;
    private ResolvedArtifacts artifacts;
    private Path classes;
    private int generation;

    public DevServer(ProjectLayout layout, Distribution distribution, PrintStream output, InputStream input) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.distribution = Objects.requireNonNull(distribution, "distribution");
        this.output = Objects.requireNonNull(output, "output");
        this.input = Objects.requireNonNull(input, "input");
        this.runtime = new ApplicationRuntime(output);
    }

    /**
     * Runs until {@code q} is entered or the JVM shuts down.
     *
     * @return the process exit code
     * @throws BuildException if the project cannot be loaded at all
     */
    public int run() {
        manifest = ProjectManifest.read(layout.root());
        output.println("Dhole Dev");
        output.println();
        output.println("Application   " + manifest.name());
        output.println("Java          " + Runtime.version().feature());
        output.println();
        Thread hook = new Thread(runtime::close, "dhole-dev-shutdown");
        Runtime.getRuntime().addShutdownHook(hook);
        startCommandReader();
        try {
            clean();
            if (resolve()) {
                reload(true);
            }
            output.println(Terminal.ok() + " Watching source files (r = restart, q = quit)");
            ChangeWatcher watcher = new ChangeWatcher(List.of(layout.mainSources(), layout.mainResources()),
                    List.of(layout.root().resolve(ProjectManifest.FILE), layout.root().resolve(".env")));
            while (true) {
                String command = commands.poll(POLL_MILLIS, TimeUnit.MILLISECONDS);
                if ("q".equals(command)) {
                    break;
                }
                List<Path> changed = watcher.changes();
                if ("r".equals(command)) {
                    output.println();
                    output.println("Restart requested");
                    reload(false);
                } else if (!changed.isEmpty()) {
                    changed = settle(watcher, changed);
                    output.println();
                    output.println("Changed: " + String.join(", ", changed.stream().map(layout::relative).toList()));
                    boolean manifestChanged = changed.contains(layout.root().resolve(ProjectManifest.FILE));
                    if (!manifestChanged || resolve()) {
                        reload(false);
                    }
                }
            }
            return 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 130;
        } finally {
            output.println();
            output.println("Stopping...");
            runtime.close();
            clean();
            try {
                Runtime.getRuntime().removeShutdownHook(hook);
            } catch (IllegalStateException ignored) {
                // The JVM is already shutting down; the hook runs.
            }
        }
    }

    /**
     * Resolves dependencies; on failure the problem is shown and the previous state kept.
     */
    private boolean resolve() {
        try {
            manifest = ProjectManifest.read(layout.root());
            artifacts = DependencyResolver.resolve(layout.root(), manifest, distribution, false);
            output.println(Terminal.ok() + " Dependencies resolved" + (artifacts.lockCreated() ? " (created " + LockFile.FILE + ")" : ""));
            return true;
        } catch (BuildException e) {
            output.println();
            output.println(e.getMessage());
            output.println();
            output.println(runtime.running() ? "Change rejected. The previous version is still running." : "Waiting for changes...");
            return false;
        }
    }

    /**
     * Compiles into a fresh directory, then replaces the running application. A compile error keeps
     * the running version; a startup error leaves the application stopped until the next change.
     */
    private void reload(boolean first) {
        if (artifacts == null) {
            output.println("Waiting for changes...");
            return;
        }
        long started = System.nanoTime();
        Path next = layout.state().resolve("dev/classes-" + (++generation));
        try {
            String warnings = ApplicationBuild.compileMain(layout, manifest, artifacts.runtime(), next);
            if (!warnings.isEmpty()) {
                output.println(warnings);
            }
        } catch (BuildException e) {
            output.println();
            output.println(e.getMessage());
            output.println();
            output.println(runtime.running() ? "Reload rejected. The previous version is still running." : "Waiting for changes...");
            return;
        }
        output.println(Terminal.ok() + " Sources compiled and metadata generated");
        runtime.stop();
        Path previous = classes;
        classes = next;
        try {
            runtime.start(artifacts.runtime(), classes, manifest.main());
            long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            output.println(Terminal.ok() + (first ? " Application started in " : " Application restarted in ") + millis + " ms");
        } catch (ApplicationRuntime.StartupFailure e) {
            output.println();
            output.println(e.getMessage());
            output.println();
            output.println("Application stopped. Waiting for changes...");
        }
        delete(previous);
    }

    private List<Path> settle(ChangeWatcher watcher, List<Path> changed) throws InterruptedException {
        Set<Path> all = new TreeSet<>(changed);
        while (true) {
            Thread.sleep(QUIET_MILLIS);
            List<Path> more = watcher.changes();
            if (more.isEmpty()) {
                return List.copyOf(all);
            }
            all.addAll(more);
        }
    }

    private void startCommandReader() {
        Thread reader = new Thread(() -> {
            try (BufferedReader lines = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                for (String line; (line = lines.readLine()) != null; ) {
                    commands.add(line.strip().toLowerCase(Locale.ROOT));
                }
            } catch (IOException ignored) {
                // Without a readable standard input, only file changes and signals drive dev mode.
            }
        }, "dhole-dev-commands");
        reader.setDaemon(true);
        reader.start();
    }

    private void clean() {
        delete(layout.state().resolve("dev"));
    }

    private static void delete(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try {
            try (Stream<Path> walk = Files.walk(directory)) {
                for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        } catch (IOException ignored) {
            // A class file still held by the JVM is removed on a later cleanup.
        }
    }
}
