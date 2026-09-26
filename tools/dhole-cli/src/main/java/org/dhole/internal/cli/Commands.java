package org.dhole.internal.cli;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.dhole.internal.build.ApplicationBuild;
import org.dhole.internal.build.ApplicationPackager;
import org.dhole.internal.build.BuildException;
import org.dhole.internal.build.DependencyResolver;
import org.dhole.internal.build.Distribution;
import org.dhole.internal.build.JavaCompilation;
import org.dhole.internal.build.LockFile;
import org.dhole.internal.build.ProjectLayout;
import org.dhole.internal.build.ProjectManifest;
import org.dhole.internal.build.ResolvedArtifacts;
import org.dhole.internal.build.Terminal;
import org.dhole.internal.build.TestRunner;
import org.dhole.internal.devtools.DevServer;

/**
 * {@code dhole build}, {@code run}, {@code test} and {@code dev}: the build pipeline (BUILD_SYSTEM.md
 * §9) followed by packaging, a JVM for the application or tests, or dev mode.
 */
final class Commands {

    private Commands() {
    }

    /**
     * The manifest, the verified artifacts and the compiled main classes.
     */
    record Compiled(ProjectLayout layout, ProjectManifest manifest, Distribution distribution, ResolvedArtifacts artifacts) {
    }

    static Compiled compile(Main.Cli cli) {
        ProjectLayout layout = cli.layout();
        ProjectManifest manifest = ProjectManifest.read(layout.root());
        JavaCompilation.compiler();
        Distribution distribution = cli.distribution();
        ResolvedArtifacts artifacts = DependencyResolver.resolve(layout.root(), manifest, distribution, cli.updateLock());
        cli.out().println(Terminal.ok() + " Dependencies resolved" + (artifacts.lockCreated() ? " (wrote " + LockFile.FILE + ")" : ""));
        String warnings = ApplicationBuild.compileMain(layout, manifest, artifacts.runtime(), layout.mainClasses());
        if (!warnings.isEmpty()) {
            cli.out().println(warnings);
        }
        cli.out().println(Terminal.ok() + " Sources compiled and metadata generated");
        return new Compiled(layout, manifest, distribution, artifacts);
    }

    static int build(Main.Cli cli) {
        Compiled compiled = compile(cli);
        Path distribution = ApplicationPackager.assemble(compiled.layout(), compiled.manifest(),
                compiled.distribution().version(), compiled.layout().mainClasses(), compiled.artifacts().runtime());
        String relative = compiled.layout().relative(distribution);
        cli.out().println(Terminal.ok() + " Packaged " + relative);
        cli.out().println();
        cli.out().println("Start it with " + relative + "/bin/" + compiled.manifest().name());
        return 0;
    }

    static int run(Main.Cli cli, List<String> arguments) {
        Compiled compiled = compile(cli);
        List<Path> classpath = new ArrayList<>();
        classpath.add(compiled.layout().mainClasses());
        classpath.addAll(compiled.artifacts().runtime());
        List<String> command = java(cli, classpath);
        command.add(compiled.manifest().main());
        command.addAll(arguments);
        return execute(cli, compiled.layout(), command, null);
    }

    static int test(Main.Cli cli, List<String> filters) {
        Compiled compiled = compile(cli);
        ProjectLayout layout = compiled.layout();
        if (!Files.isDirectory(layout.testSources())) {
            cli.out().println("No tests: " + layout.relative(layout.testSources()) + " does not exist.");
            return 0;
        }
        String warnings = ApplicationBuild.compileTests(layout, compiled.manifest(), layout.mainClasses(),
                compiled.artifacts(), layout.testClasses());
        if (!warnings.isEmpty()) {
            cli.out().println(warnings);
        }
        cli.out().println(Terminal.ok() + " Tests compiled");
        List<Path> classpath = new ArrayList<>();
        classpath.add(layout.testClasses());
        classpath.add(layout.mainClasses());
        classpath.addAll(compiled.artifacts().runtime());
        classpath.addAll(compiled.artifacts().test());
        classpath.add(location(TestRunner.class));
        List<String> command = java(cli, classpath);
        command.add(TestRunner.class.getName());
        command.add(layout.testClasses().toString());
        command.addAll(filters);
        cli.out().println();
        return execute(cli, layout, command, "test");
    }

    static int dev(Main.Cli cli) {
        JavaCompilation.compiler();
        return new DevServer(cli.layout(), cli.distribution(), cli.out(), cli.in()).run();
    }

    private static List<String> java(Main.Cli cli, List<Path> classpath) {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        if (cli.verbose()) {
            command.add("-Ddhole.verbose=true");
        }
        command.add("-cp");
        command.add(String.join(File.pathSeparator, classpath.stream().map(Path::toString).toList()));
        return command;
    }

    private static int execute(Main.Cli cli, ProjectLayout layout, List<String> command, String environment) {
        ProcessBuilder builder = new ProcessBuilder(command).directory(layout.root().toFile()).inheritIO();
        if (environment != null) {
            builder.environment().put("APP_ENV", environment);
        }
        try {
            Process process = builder.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (process.isAlive()) {
                    process.descendants().forEach(ProcessHandle::destroy);
                    process.destroy();
                }
            }, "dhole-run-shutdown"));
            return process.waitFor();
        } catch (IOException e) {
            throw new BuildException("Run Error\n\nUnable to start Java: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 130;
        }
    }

    private static Path location(Class<?> type) {
        try {
            return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
