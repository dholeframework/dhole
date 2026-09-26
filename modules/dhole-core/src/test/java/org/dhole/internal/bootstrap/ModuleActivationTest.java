package org.dhole.internal.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.dhole.Dhole;
import org.dhole.internal.module.ActivationContext;
import org.dhole.internal.module.ModuleActivator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Bootstrap with a build-generated {@code modules.idx}, in an isolated class loader (as dev mode
 * does) and in a separate JVM through {@code Dhole.run}.
 */
class ModuleActivationTest {

    private static final String PREFIX = "org.dhole.internal.bootstrap.ModuleActivationTest$";

    @TempDir
    Path metadata;

    @Test
    void launcherActivatesIndexedModulesAndClosingStopsThem() throws Exception {
        writeIndex("""
                dhole-modules 1

                module base
                activator %1$sRecordingActivator

                module web
                activator %1$sRecordingActivator
                requires base
                """.formatted(PREFIX));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (URLClassLoader loader = isolatedLoader()) {
            Class<?> application = loader.loadClass(PREFIX + "SampleApp");
            AutoCloseable running = (AutoCloseable) loader.loadClass(Launcher.class.getName())
                    .getMethod("start", Class.class, PrintStream.class)
                    .invoke(null, application, new PrintStream(output, true, StandardCharsets.UTF_8));
            running.close();
        }

        assertEquals(List.of("Dhole", "", "Application starting...", "start 1 SampleApp", "start 2 SampleApp",
                "Application ready.", "stop 2", "stop 1", "Application stopped."),
                output.toString(StandardCharsets.UTF_8).lines().toList());
    }

    @Test
    void launcherStartupFailureRollsBackAndPropagates() throws Exception {
        writeIndex("""
                dhole-modules 1

                module base
                activator %1$sRecordingActivator

                module web
                activator %1$sFailingActivator
                requires base
                """.formatted(PREFIX));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        Throwable failure;
        try (URLClassLoader loader = isolatedLoader()) {
            Class<?> application = loader.loadClass(PREFIX + "SampleApp");
            failure = org.junit.jupiter.api.Assertions.assertThrows(InvocationTargetException.class,
                    () -> loader.loadClass(Launcher.class.getName()).getMethod("start", Class.class, PrintStream.class)
                            .invoke(null, application, new PrintStream(output, true, StandardCharsets.UTF_8)))
                    .getCause();
        }

        assertEquals("Port 8080 is already in use.\n\n(module 'web' failed to start)", failure.getMessage());
        assertEquals(List.of("Dhole", "", "Application starting...", "start 1 SampleApp",
                "stop 1", "Application failed to start."), output.toString(StandardCharsets.UTF_8).lines().toList());
    }

    @Test
    void dholeRunReportsStartupFailureAndExitsWithStatusOne() throws IOException, InterruptedException {
        writeIndex("dhole-modules 1\n\nmodule web\nactivator " + PREFIX + "FailingActivator\n");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(java, "-cp", metadata + File.pathSeparator + System.getProperty("java.class.path"),
                PREFIX + "SampleApp")
                .redirectErrorStream(true)
                .start();

        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "sample application did not exit");
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertEquals(1, process.exitValue(), output);
        assertEquals(List.of("Dhole", "", "Application starting...", "Application failed to start.", "",
                "Port 8080 is already in use.", "", "(module 'web' failed to start)", "", "Application startup aborted."),
                output.lines().toList());
    }

    @Test
    void invalidModuleGraphFailsBeforeStarting() throws IOException, InterruptedException {
        writeIndex("dhole-modules 1\n\nmodule web\nrequires routing\n");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(java, "-cp", metadata + File.pathSeparator + System.getProperty("java.class.path"),
                PREFIX + "SampleApp")
                .redirectErrorStream(true)
                .start();

        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "sample application did not exit");
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertEquals(1, process.exitValue(), output);
        assertTrue(output.contains("Module Dependency Error\n\nModule 'web' requires module 'routing'")
                || output.contains("Module Dependency Error\r\n\r\nModule 'web' requires module 'routing'"), output);
        assertTrue(!output.contains("Application starting..."), output);
    }

    private void writeIndex(String text) throws IOException {
        Path file = metadata.resolve("META-INF/dhole/modules.idx");
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    /**
     * The metadata directory plus the test class path, isolated from the test's own loader.
     */
    private URLClassLoader isolatedLoader() throws IOException {
        List<URL> urls = new ArrayList<>();
        urls.add(metadata.toUri().toURL());
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            urls.add(Path.of(entry).toUri().toURL());
        }
        return new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader());
    }

    public static final class SampleApp {

        private SampleApp() {
        }

        public static void main(String[] args) {
            Dhole.run(SampleApp.class);
        }
    }

    public static final class RecordingActivator implements ModuleActivator {

        private static int count;
        private final int number = ++count;
        private PrintStream output;

        @Override
        public void start(ActivationContext context) {
            output = context.output();
            output.println("start " + number + " " + context.applicationClass().getSimpleName());
        }

        @Override
        public void stop() {
            output.println("stop " + number);
        }
    }

    public static final class FailingActivator implements ModuleActivator {

        @Override
        public void start(ActivationContext context) {
            throw new IllegalStateException("Port 8080 is already in use.");
        }

        @Override
        public void stop() {
        }
    }
}
