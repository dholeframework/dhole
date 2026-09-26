package org.dhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DholeTest {

    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
    private PrintStream originalOut;

    @BeforeEach
    void captureStandardOutput() {
        originalOut = System.out;
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreStandardOutput() {
        System.setOut(originalOut);
    }

    @Test
    void runBootstrapsAndStartsTheApplication() {
        Dhole.run(DholeTest.class);

        assertEquals(List.of("Dhole", "", "Application starting...", "Application ready."),
                captured.toString(StandardCharsets.UTF_8).lines().toList());
    }

    @Test
    void runRequiresAnApplicationClass() {
        assertThrows(NullPointerException.class, () -> Dhole.run(null));
    }

    @Test
    void shutdownHookStopsTheApplicationWhenTheJvmExits() throws IOException, InterruptedException {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(
                java, "-cp", System.getProperty("java.class.path"), SampleApp.class.getName())
                .redirectErrorStream(true)
                .start();

        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "sample application did not exit");
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertEquals(0, process.exitValue(), output);
        assertEquals(
                List.of("Dhole", "", "Application starting...", "Application ready.",
                        "Application stopped."),
                output.lines().toList());
    }

    /**
     * Minimal application run in a separate JVM; the JVM exits when {@code main} returns.
     */
    static final class SampleApp {

        private SampleApp() {
        }

        public static void main(String[] args) {
            Dhole.run(SampleApp.class);
        }
    }
}
