package org.dhole.internal.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The compile steps of the Dhole build pipeline (BUILD_SYSTEM.md §9): compile Java with the
 * metadata compiler, copy resources and write {@code modules.idx}. Output is staged and replaces
 * the previous output only when compilation succeeds, so a failed build never leaves half-written
 * classes behind.
 */
public final class ApplicationBuild {

    private ApplicationBuild() {
    }

    /**
     * Compiles the main source set into {@code output}.
     *
     * @return compiler warnings, empty when there are none
     * @throws CompilationException if the sources do not compile
     */
    public static String compileMain(ProjectLayout layout, ProjectManifest manifest, List<Path> runtime, Path output) {
        if (JavaCompilation.javaFiles(layout.mainSources()).isEmpty()) {
            throw new BuildException("Build Error\n\nNo Java sources in " + layout.relative(layout.mainSources())
                    + ".\n\nThe application class " + manifest.main() + " must be there.");
        }
        return staged(output, staging -> {
            JavaCompilation.Result result = JavaCompilation.compile(layout, layout.mainSources(), staging, runtime,
                    manifest.java(), Optional.of(manifest.main()));
            if (!result.success()) {
                throw new CompilationException(result.report());
            }
            JavaCompilation.copyResources(layout.mainResources(), staging);
            ModulesIndexGenerator.write(runtime, staging);
            return result.report();
        });
    }

    /**
     * Compiles the test source set against the main classes, runtime and test artifacts.
     *
     * @return compiler warnings, empty when there are none
     * @throws CompilationException if the tests do not compile
     */
    public static String compileTests(ProjectLayout layout, ProjectManifest manifest, Path mainClasses,
            ResolvedArtifacts artifacts, Path output) {
        List<Path> classpath = new ArrayList<>();
        classpath.add(mainClasses);
        classpath.addAll(artifacts.runtime());
        classpath.addAll(artifacts.test());
        return staged(output, staging -> {
            JavaCompilation.Result result = JavaCompilation.compile(layout, layout.testSources(), staging, classpath,
                    manifest.java(), Optional.empty());
            if (!result.success()) {
                throw new CompilationException(result.report());
            }
            JavaCompilation.copyResources(layout.testResources(), staging);
            return result.report();
        });
    }

    private static String staged(Path output, Step step) {
        Path staging = output.resolveSibling(output.getFileName() + ".staging");
        try {
            ApplicationPackager.delete(staging);
            Files.createDirectories(staging);
            String report = step.run(staging);
            ApplicationPackager.delete(output);
            Files.move(staging, output, StandardCopyOption.ATOMIC_MOVE);
            return report;
        } catch (IOException e) {
            throw new BuildException("Build Error\n\nUnable to write " + output + ": " + e.getMessage(), e);
        } finally {
            try {
                ApplicationPackager.delete(staging);
            } catch (IOException ignored) {
                // Best effort: a leftover staging directory is replaced by the next build.
            }
        }
    }

    @FunctionalInterface
    private interface Step {
        String run(Path staging) throws IOException;
    }

    /**
     * The sources do not compile; the message is the rendered compiler report.
     */
    public static final class CompilationException extends BuildException {

        private static final long serialVersionUID = 1L;

        public CompilationException(String report) {
            super(report);
        }
    }
}
