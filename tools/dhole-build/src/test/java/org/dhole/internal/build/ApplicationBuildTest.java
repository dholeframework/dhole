package org.dhole.internal.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApplicationBuildTest {

    @TempDir
    Path root;

    private final ProjectManifest manifest =
            new ProjectManifest("hello", "0.1.0", 21, "0.1.0", Map.of(), "hello.App");

    @Test
    void compilesWithMetadataResourcesAndModulesIndex() throws IOException {
        ProjectLayout layout = project("""
                package hello;

                public final class App {
                    public static void main(String[] args) {
                        System.out.println("hello " + String.join(",", args));
                    }
                }
                """);
        write("src/main/resources/banner.txt", "Hello");

        String warnings = ApplicationBuild.compileMain(layout, manifest, List.of(), layout.mainClasses());

        assertEquals("", warnings);
        assertTrue(Files.exists(layout.mainClasses().resolve("hello/App.class")));
        assertEquals("Hello", Files.readString(layout.mainClasses().resolve("banner.txt")));
        assertTrue(Files.readString(layout.mainClasses().resolve("META-INF/dhole/components.idx")).startsWith("dhole-metadata 1"));
        assertEquals("dhole-modules 1\n", Files.readString(layout.mainClasses().resolve("META-INF/dhole/modules.idx")));
        assertFalse(Files.exists(layout.mainClasses().resolveSibling("main.staging")));
    }

    @Test
    void compileErrorsAreRenderedAndKeepThePreviousOutput() throws IOException {
        ProjectLayout layout = project("package hello;\n\npublic final class App {\n}\n");
        ApplicationBuild.compileMain(layout, manifest, List.of(), layout.mainClasses());
        write("src/main/java/hello/App.java", "package hello;\n\npublic final class App {\n    long id = \"text\";\n}\n");

        ApplicationBuild.CompilationException failure = assertThrows(ApplicationBuild.CompilationException.class,
                () -> ApplicationBuild.compileMain(layout, manifest, List.of(), layout.mainClasses()));

        assertTrue(failure.getMessage().startsWith("Build Error\n\nsrc/main/java/hello/App.java:4\n\nincompatible types: "
                + "java.lang.String cannot be converted to long"), failure.getMessage());
        assertTrue(Files.exists(layout.mainClasses().resolve("hello/App.class")), "previous output kept");
    }

    @Test
    void metadataDiagnosticsFailTheBuild() throws IOException {
        ProjectLayout layout = project("package hello;\n\npublic final class App {\n}\n");
        write("src/main/java/hello/CreateUser.java", """
                package hello;

                public record CreateUser(String name) implements org.dhole.validation.Validatable {
                }
                """);

        ApplicationBuild.CompilationException failure = assertThrows(ApplicationBuild.CompilationException.class,
                () -> ApplicationBuild.compileMain(layout, manifest, testClassPath(), layout.mainClasses()));

        assertTrue(failure.getMessage().contains("src/main/java/hello/CreateUser.java:3\n\nDhole Error DHOLE-VAL-001"), failure.getMessage());
    }

    @Test
    void missingSourcesAreExplained() {
        BuildException failure = assertThrows(BuildException.class,
                () -> ApplicationBuild.compileMain(new ProjectLayout(root), manifest, List.of(), root.resolve("out")));

        assertTrue(failure.getMessage().contains("No Java sources in src/main/java"), failure.getMessage());
    }

    @Test
    void packagedDistributionRunsWithJavaJarAndIsReproducible(@TempDir Path home) throws Exception {
        ProjectLayout layout = project("""
                package hello;

                public final class App {
                    public static void main(String[] args) throws Exception {
                        Class.forName("org.dhole.Marker");
                        System.out.println("hello " + String.join(",", args));
                    }
                }
                """);
        Path runtime = runtimeJar(home);
        ApplicationBuild.compileMain(layout, manifest, List.of(runtime), layout.mainClasses());

        Path distribution = ApplicationPackager.assemble(layout, manifest, "0.1.0", layout.mainClasses(), List.of(runtime));

        Path jar = distribution.resolve("lib/hello.jar");
        try (JarFile file = new JarFile(jar.toFile())) {
            Attributes attributes = file.getManifest().getMainAttributes();
            assertEquals("hello.App", attributes.getValue(Attributes.Name.MAIN_CLASS));
            assertEquals("marker-1.0.jar", attributes.getValue(Attributes.Name.CLASS_PATH));
            assertEquals(List.of("META-INF/", "META-INF/MANIFEST.MF", "META-INF/dhole/", "META-INF/dhole/components.idx",
                    "META-INF/dhole/modules.idx", "META-INF/dhole/routes.idx", "META-INF/dhole/validation.idx", "hello/",
                    "hello/App.class"), file.stream().map(entry -> entry.getName()).toList());
        }
        assertEquals(-1L, Files.mismatch(runtime, distribution.resolve("lib/marker-1.0.jar")));
        assertTrue(Files.readString(distribution.resolve("bin/hello")).contains("-jar \"$APP_HOME/lib/hello.jar\" \"$@\""));
        assertTrue(Files.readString(distribution.resolve("bin/hello.cmd")).contains("-jar \"%APP_HOME%\\lib\\hello.jar\" %*\r\n"));
        String scripts = Files.readString(distribution.resolve("bin/hello")) + Files.readString(distribution.resolve("bin/hello.cmd"));
        assertFalse(scripts.contains(root.toString()) || scripts.contains(home.toString()), "no absolute paths");

        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(java, "-jar", jar.toString(), "a", "b c").redirectErrorStream(true).start();
        assertTrue(process.waitFor(60, TimeUnit.SECONDS));
        assertEquals("hello a,b c", new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip());
        assertEquals(0, process.exitValue());

        byte[] first = Files.readAllBytes(jar);
        ApplicationBuild.compileMain(layout, manifest, List.of(runtime), layout.mainClasses());
        ApplicationPackager.assemble(layout, manifest, "0.1.0", layout.mainClasses(), List.of(runtime));
        assertEquals(-1L, Arrays.mismatch(first, Files.readAllBytes(jar)), "reproducible application JAR");
    }

    private Path runtimeJar(Path home) throws IOException {
        ProjectLayout marker = new ProjectLayout(home.resolve("marker"));
        Path source = marker.mainSources().resolve("org/dhole/Marker.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package org.dhole;\n\npublic final class Marker {\n}\n");
        JavaCompilation.Result result = JavaCompilation.compile(marker, marker.mainSources(), marker.mainClasses(), List.of(),
                21, Optional.empty());
        assertTrue(result.success(), result.report());
        Path jar = home.resolve("marker-1.0.jar");
        ApplicationPackager.writeJar(marker.mainClasses(), jar, "org.dhole.Marker", List.of(), "0.1.0");
        return jar;
    }

    private ProjectLayout project(String app) throws IOException {
        write("src/main/java/hello/App.java", app);
        return new ProjectLayout(root);
    }

    private void write(String path, String text) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static List<Path> testClassPath() {
        return Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
                .map(Path::of).toList();
    }
}
