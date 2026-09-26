package org.dhole.internal.devtools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.dhole.Dhole;
import org.dhole.internal.build.ApplicationBuild;
import org.dhole.internal.build.ProjectLayout;
import org.dhole.internal.build.ProjectManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Fast restart: every start uses a new application class loader and the modules named by the new
 * build's modules.idx; stopping stops the modules.
 */
class ApplicationRuntimeTest {

    @TempDir
    Path root;

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final ProjectManifest manifest = new ProjectManifest("hello", "0.1.0", 21, "0.1.0", Map.of(), "hello.App");

    @Test
    void restartRunsTheNewCodeInAFreshClassLoader() throws Exception {
        ProjectLayout layout = new ProjectLayout(root);
        List<Path> runtime = List.of(core());
        try (ApplicationRuntime application = new ApplicationRuntime(new PrintStream(output, true, StandardCharsets.UTF_8))) {
            Path first = build(layout, runtime, "first", 1);
            application.start(runtime, first, "hello.App");
            assertTrue(application.running());
            application.stop();
            assertFalse(application.running());

            Path second = build(layout, runtime, "second", 2);
            application.start(runtime, second, "hello.App");
            application.stop();
        }

        String text = output.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertTrue(text.contains("start first loader dhole-application\n"), text);
        assertTrue(text.contains("stop first\n"), text);
        assertTrue(text.contains("start second loader dhole-application\n"), text);
        assertTrue(text.indexOf("stop first") < text.indexOf("start second"), text);
        assertEquals(2, text.split("Application stopped.", -1).length - 1, text);
    }

    @Test
    void startupFailureLeavesNothingRunning() throws Exception {
        ProjectLayout layout = new ProjectLayout(root);
        List<Path> runtime = List.of(core());
        Path classes = build(layout, runtime, "failing", 1);
        Files.writeString(classes.resolve("META-INF/dhole/modules.idx"),
                "dhole-modules 1\n\nmodule greeting\nactivator hello.Missing\n");

        try (ApplicationRuntime application = new ApplicationRuntime(new PrintStream(output, true, StandardCharsets.UTF_8))) {
            ApplicationRuntime.StartupFailure failure = assertThrows(ApplicationRuntime.StartupFailure.class,
                    () -> application.start(runtime, classes, "hello.App"));
            assertTrue(failure.getMessage().startsWith("Module Error\n\nModule 'greeting' cannot be activated"),
                    failure.getMessage());
            assertFalse(application.running());
        }
    }

    private Path build(ProjectLayout layout, List<Path> runtime, String name, int generation) throws IOException {
        write("src/main/java/hello/App.java", "package hello;\n\npublic final class App {\n}\n");
        write("src/main/java/hello/Greeting.java", """
                package hello;

                import org.dhole.internal.module.ActivationContext;
                import org.dhole.internal.module.ModuleActivator;

                public final class Greeting implements ModuleActivator {

                    private java.io.PrintStream output;

                    @Override
                    public void start(ActivationContext context) {
                        output = context.output();
                        output.println("start %1$s loader " + getClass().getClassLoader().getName());
                    }

                    @Override
                    public void stop() {
                        output.println("stop %1$s");
                    }
                }
                """.formatted(name));
        Path classes = layout.state().resolve("dev/classes-" + generation);
        ApplicationBuild.compileMain(layout, manifest, runtime, classes);
        Files.writeString(classes.resolve("META-INF/dhole/modules.idx"),
                "dhole-modules 1\n\nmodule greeting\nactivator hello.Greeting\n");
        return classes;
    }

    private void write(String path, String text) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static Path core() throws URISyntaxException {
        return Path.of(Dhole.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }
}
