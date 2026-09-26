package org.dhole.internal.build;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.dhole.internal.compiler.MetadataProcessor;

/**
 * Compiles a source set with the JDK compiler in-process (BUILD_SYSTEM.md §21). The main source set
 * runs the Dhole metadata compiler with {@code -Adhole.application} from {@code [build] main};
 * other source sets run no annotation processing. Compiler errors are rendered for developers:
 *
 * <pre>
 * Build Error
 *
 * src/main/java/hello/UserService.java:42
 *
 * incompatible types: String cannot be converted to long
 * </pre>
 */
public final class JavaCompilation {

    private JavaCompilation() {
    }

    /**
     * Result of one compilation.
     *
     * @param report rendered errors (on failure) and warnings; empty when there are none
     */
    public record Result(boolean success, String report) {
    }

    /**
     * Returns the JDK compiler or explains why there is none.
     *
     * @throws BuildException if the running Java is not a JDK
     */
    public static JavaCompiler compiler() {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new BuildException("Java Error\n\nThe Java compiler (javac) is not available in " + System.getProperty("java.home")
                    + ".\n\nDhole needs a JDK 21 or newer, not a JRE. Install a JDK and set JAVA_HOME.");
        }
        return compiler;
    }

    /**
     * Compiles every {@code .java} file under {@code sources} into {@code output}.
     *
     * @param application the application class for metadata generation, or empty for no processing
     * @throws BuildException if the compiler cannot run at all
     */
    public static Result compile(ProjectLayout layout, Path sources, Path output, List<Path> classpath, int release,
            Optional<String> application) {
        int running = Runtime.version().feature();
        if (release > running) {
            throw new BuildException("Java Error\n\ndhole.toml targets Java " + release + " but Dhole is running on Java "
                    + running + ".\n\nRun Dhole with a JDK " + release + " or newer.");
        }
        List<Path> files = javaFiles(sources);
        if (files.isEmpty()) {
            return new Result(true, "");
        }
        JavaCompiler compiler = compiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<String> options = new ArrayList<>(List.of("--release", Integer.toString(release), "-encoding", "UTF-8",
                "-d", output.toString(), "-implicit:class"));
        if (!classpath.isEmpty()) {
            options.add("-classpath");
            options.add(String.join(File.pathSeparator, classpath.stream().map(Path::toString).toList()));
        }
        application.ifPresentOrElse(main -> options.add("-A" + MetadataProcessor.APPLICATION_OPTION + "=" + main),
                () -> options.add("-proc:none"));
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            Files.createDirectories(output);
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null,
                    fileManager.getJavaFileObjectsFromPaths(files));
            if (application.isPresent()) {
                task.setProcessors(List.of(new MetadataProcessor()));
            }
            boolean success = task.call();
            return new Result(success, render(layout, diagnostics.getDiagnostics(), success));
        } catch (IOException e) {
            throw new BuildException("Build Error\n\nUnable to compile: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new BuildException("Build Error\n\nThe Java compiler failed: " + e, e);
        }
    }

    /**
     * Copies a resource directory into a class output directory, keeping relative paths.
     */
    public static void copyResources(Path resources, Path output) {
        if (!Files.isDirectory(resources)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(resources)) {
            for (Path file : walk.filter(Files::isRegularFile).sorted().toList()) {
                Path target = output.resolve(resources.relativize(file).toString());
                Files.createDirectories(target.getParent());
                Files.copy(file, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BuildException("Build Error\n\nUnable to copy resources: " + e.getMessage(), e);
        }
    }

    static List<Path> javaFiles(Path sources) {
        if (!Files.isDirectory(sources)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(sources)) {
            return walk.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".java"))
                    .sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String render(ProjectLayout layout, List<Diagnostic<? extends JavaFileObject>> diagnostics,
            boolean success) {
        StringBuilder report = new StringBuilder();
        List<Diagnostic<? extends JavaFileObject>> errors = diagnostics.stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR).toList();
        List<Diagnostic<? extends JavaFileObject>> warnings = diagnostics.stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.WARNING
                        || diagnostic.getKind() == Diagnostic.Kind.MANDATORY_WARNING).toList();
        if (!success) {
            report.append("Build Error\n");
            errors.forEach(diagnostic -> append(report, layout, diagnostic));
            if (errors.isEmpty()) {
                report.append("\nCompilation failed.\n");
            }
        }
        if (!warnings.isEmpty()) {
            report.append(report.isEmpty() ? "" : "\n").append("Warnings\n");
            warnings.forEach(diagnostic -> append(report, layout, diagnostic));
        }
        return report.toString().stripTrailing();
    }

    private static void append(StringBuilder report, ProjectLayout layout, Diagnostic<? extends JavaFileObject> diagnostic) {
        report.append('\n');
        if (diagnostic.getSource() != null) {
            report.append(layout.relative(Path.of(diagnostic.getSource().toUri())));
            if (diagnostic.getLineNumber() != Diagnostic.NOPOS) {
                report.append(':').append(diagnostic.getLineNumber());
            }
            report.append("\n\n");
        }
        report.append(diagnostic.getMessage(Locale.ROOT).stripTrailing()).append('\n');
    }
}
