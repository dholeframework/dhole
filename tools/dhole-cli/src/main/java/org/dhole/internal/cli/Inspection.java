package org.dhole.internal.cli;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.tools.ToolProvider;

import org.dhole.internal.build.BuildException;
import org.dhole.internal.build.DependencyResolver;
import org.dhole.internal.build.Distribution;
import org.dhole.internal.build.ProjectLayout;
import org.dhole.internal.build.ProjectManifest;
import org.dhole.internal.build.Terminal;
import org.dhole.internal.web.RouteMetadata;

/**
 * {@code dhole routes}, {@code config} and {@code doctor}: they read build metadata and
 * configuration; none starts the server.
 */
final class Inspection {

    private static final String CONFIGURATION = "org.dhole.internal.config.Configuration";
    private static final String ENVIRONMENT = "org.dhole.config.Environment";
    private static final String DIAGNOSTICS = "org.dhole.internal.web.WebDiagnostics";

    private Inspection() {
    }

    /**
     * Compiles, then prints the typed routes from {@code routes.idx}.
     */
    static int routes(Main.Cli cli) {
        Commands.Compiled compiled = Commands.compile(cli);
        Path index = compiled.layout().mainClasses().resolve(RouteMetadata.LOCATION);
        List<RouteMetadata.Entry> routes;
        try {
            routes = Files.exists(index)
                    ? RouteMetadata.parse(Files.readString(index, StandardCharsets.UTF_8)).routes()
                    : List.of();
        } catch (IOException e) {
            throw new BuildException("Build Error\n\nUnable to read " + RouteMetadata.LOCATION + ": " + e.getMessage(), e);
        }
        cli.out().println();
        if (routes.isEmpty()) {
            cli.out().println("No typed routes.");
            return 0;
        }
        List<RouteMetadata.Entry> sorted = routes.stream()
                .sorted(Comparator.comparing(RouteMetadata.Entry::path).thenComparing(entry -> entry.method().ordinal()))
                .toList();
        int pathWidth = Math.max("PATH".length(), sorted.stream().mapToInt(entry -> entry.path().length()).max().orElse(0));
        cli.out().println(pad("METHOD", 8) + pad("PATH", pathWidth + 2) + "HANDLER");
        for (RouteMetadata.Entry entry : sorted) {
            String controller = entry.controller().substring(entry.controller().lastIndexOf('.') + 1).replace('$', '.');
            cli.out().println(pad(entry.method().name(), 8) + pad(entry.path(), pathWidth + 2) + controller + "." + entry.handler());
        }
        return 0;
    }

    /**
     * {@code dhole config}: the configuration values read, secrets masked. {@code dhole config check}:
     * validates the configuration.
     */
    static int config(Main.Cli cli, List<String> arguments) {
        boolean check = arguments.equals(List.of("check"));
        if (!arguments.isEmpty() && !check) {
            cli.err().println("Usage: dhole config [check]");
            return 2;
        }
        Commands.Compiled compiled = Commands.compile(cli);
        try (URLClassLoader loader = applicationLoader(compiled.layout().mainClasses(), compiled.artifacts().runtime())) {
            Loaded configuration = loadConfiguration(loader, compiled.manifest().main());
            cli.out().println();
            if (check) {
                cli.out().println(Terminal.ok() + " Configuration valid (" + configuration.environment() + ")");
            } else {
                cli.out().println("Environment   " + configuration.environment());
                cli.out().println();
                cli.out().println(configuration.report().isEmpty() ? "No environment values are read." : configuration.report());
            }
            return 0;
        } catch (IOException e) {
            throw new BuildException("Configuration Error\n\n" + e.getMessage(), e);
        }
    }

    /**
     * {@code dhole doctor}: read-only checks of the environment, manifest, lock, metadata,
     * configuration and {@code .env} Git status. Never compiles or writes.
     */
    static int doctor(Main.Cli cli) {
        Report report = new Report(cli);
        cli.out().println("Dhole Doctor");

        report.section("Java");
        int feature = Runtime.version().feature();
        report.check(feature >= 21, "Java " + System.getProperty("java.version"), "Java " + feature + " is too old; Dhole needs 21+");
        report.check(ToolProvider.getSystemJavaCompiler() != null, "javac available", "javac missing: install a JDK, not a JRE");

        report.section("Project");
        ProjectLayout layout = cli.layout();
        ProjectManifest manifest;
        try {
            manifest = ProjectManifest.read(layout.root());
            report.ok(ProjectManifest.FILE + " valid");
        } catch (BuildException e) {
            report.failed(e.getMessage().replace("Manifest Error\n\n", ""));
            return report.finish();
        }
        Distribution distribution = cli.distribution();
        report.check(manifest.dholeVersion().equals(distribution.version()), "Dhole " + distribution.version() + " installed",
                "dhole.toml requires Dhole " + manifest.dholeVersion() + " but " + distribution.version() + " is installed");
        try {
            int verified = DependencyResolver.verify(layout.root(), manifest, distribution);
            report.ok("dhole.lock valid (" + verified + " artifacts verified)");
        } catch (BuildException e) {
            if (!Files.exists(layout.root().resolve("dhole.lock"))) {
                report.warning(e.getMessage());
            } else {
                report.failed(e.getMessage().replaceFirst("^\\w+ Error\n\n", ""));
            }
        }

        report.section("Metadata");
        Path classes = layout.mainClasses();
        boolean built = Files.exists(classes.resolve("META-INF/dhole/modules.idx"));
        URLClassLoader loader = null;
        if (!built) {
            report.warning("not built yet; run 'dhole build' to generate metadata");
        } else {
            if (newer(layout.mainSources(), classes.resolve("META-INF/dhole/modules.idx"))) {
                report.warning("sources changed since the last build; results describe the previous build");
            }
            try {
                loader = applicationLoader(classes, runtime(layout, manifest, distribution));
                for (String result : diagnostics(loader)) {
                    boolean ok = result.startsWith("ok ");
                    report.check(ok, result.substring(3), result.substring(result.indexOf(' ') + 1));
                }
            } catch (BuildException | IOException e) {
                report.failed(e.getMessage());
            }
        }

        report.section("Configuration");
        if (loader != null) {
            try {
                Loaded configuration = loadConfiguration(loader, manifest.main());
                report.ok("configuration valid (" + configuration.environment() + ")");
            } catch (BuildException e) {
                report.failed(e.getMessage().replace("Configuration Error\n\n", ""));
            } finally {
                close(loader);
            }
        } else {
            report.warning("not checked; the application is not built");
        }
        gitStatus(report, layout.root());
        return report.finish();
    }

    private static void gitStatus(Report report, Path root) {
        if (!Files.exists(root.resolve(".env"))) {
            report.ok("no .env file");
            return;
        }
        GitResult tracked = git(root, "ls-files", "--error-unmatch", ".env");
        if (tracked == null) {
            report.warning("Git not available; cannot check that .env is ignored");
            return;
        }
        if (tracked.exitCode() == 0) {
            report.failed(".env is tracked by Git; remove it with 'git rm --cached .env' and keep it ignored");
            return;
        }
        GitResult ignored = git(root, "check-ignore", "-q", ".env");
        if (ignored.exitCode() == 0) {
            report.ok(".env ignored by Git");
        } else if (ignored.exitCode() == 1) {
            report.failed(".env is not ignored by Git; add it to .gitignore");
        } else {
            report.warning("not a Git repository; cannot check that .env is ignored");
        }
    }

    record GitResult(int exitCode) {
    }

    private static GitResult git(Path root, String... arguments) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        try {
            Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            return new GitResult(process.exitValue());
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static List<Path> runtime(ProjectLayout layout, ProjectManifest manifest, Distribution distribution) {
        if (!Files.exists(layout.root().resolve("dhole.lock"))) {
            throw new BuildException("dhole.lock is missing; run 'dhole build'");
        }
        return DependencyResolver.resolve(layout.root(), manifest, distribution, false).runtime();
    }

    @SuppressWarnings("unchecked")
    private static List<String> diagnostics(URLClassLoader loader) {
        try {
            return (List<String>) loader.loadClass(DIAGNOSTICS).getMethod("check", ClassLoader.class).invoke(null, loader);
        } catch (ClassNotFoundException e) {
            return List.of("ok no web module; nothing to check");
        } catch (InvocationTargetException e) {
            throw new BuildException(String.valueOf(e.getCause().getMessage()), e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    record Loaded(String environment, String report) {
    }

    private static Loaded loadConfiguration(URLClassLoader loader, String main) {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(loader);
        try {
            Class<?> configuration = loader.loadClass(CONFIGURATION);
            Class<?> application = Class.forName(main, false, loader);
            Object loaded = configuration.getMethod("load", Class.class).invoke(null, application);
            Object environment = configuration.getMethod("environment").invoke(loaded);
            String name = (String) loader.loadClass(ENVIRONMENT).getMethod("name").invoke(environment);
            return new Loaded(name, (String) configuration.getMethod("report").invoke(loaded));
        } catch (ClassNotFoundException e) {
            throw new BuildException("Configuration Error\n\nThis application does not use Dhole configuration "
                    + "(add web or config to [dependencies]), or " + main + " does not exist.");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            throw new BuildException(cause.getMessage() == null ? cause.toString() : cause.getMessage(), cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private static URLClassLoader applicationLoader(Path classes, List<Path> runtime) throws MalformedURLException {
        List<URL> urls = new ArrayList<>();
        urls.add(classes.toUri().toURL());
        for (Path artifact : runtime) {
            urls.add(artifact.toUri().toURL());
        }
        return new URLClassLoader("dhole-inspection", urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader());
    }

    private static boolean newer(Path sources, Path reference) {
        try (Stream<Path> walk = Files.walk(sources)) {
            FileTime built = Files.getLastModifiedTime(reference);
            return walk.filter(Files::isRegularFile).anyMatch(path -> {
                try {
                    return Files.getLastModifiedTime(path).compareTo(built) > 0;
                } catch (IOException e) {
                    return false;
                }
            });
        } catch (IOException e) {
            return false;
        }
    }

    private static void close(URLClassLoader loader) {
        try {
            loader.close();
        } catch (IOException ignored) {
            // Only releases file handles.
        }
    }

    private static String pad(String text, int width) {
        return text.length() >= width ? text + " " : text + " ".repeat(width - text.length());
    }

    /**
     * Doctor output: sections of checks, then a verdict. Failures make the exit code 1.
     */
    private static final class Report {

        private final Main.Cli cli;
        private int failures;

        Report(Main.Cli cli) {
            this.cli = cli;
        }

        void section(String name) {
            cli.out().println();
            cli.out().println(name);
        }

        void check(boolean ok, String success, String failure) {
            if (ok) {
                ok(success);
            } else {
                failed(failure);
            }
        }

        void ok(String message) {
            cli.out().println("  " + Terminal.ok() + " " + message);
        }

        void warning(String message) {
            cli.out().println("  " + Terminal.warning() + " " + message.replace("\n", "\n    "));
        }

        void failed(String message) {
            failures++;
            cli.out().println("  " + Terminal.failed() + " " + message.strip().replace("\n", "\n    "));
        }

        int finish() {
            cli.out().println();
            cli.out().println(failures == 0 ? "No critical problems found." : failures + " problem(s) found.");
            return failures == 0 ? 0 : 1;
        }
    }
}
