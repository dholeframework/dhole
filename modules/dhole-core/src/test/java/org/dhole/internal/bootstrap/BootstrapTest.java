package org.dhole.internal.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.dhole.application.Application;
import org.dhole.application.ApplicationState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BootstrapTest {

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
    void buildAssemblesACreatedApplicationWithAContext() {
        Application application = Bootstrap.create(BootstrapTest.class).build();

        assertEquals(ApplicationState.CREATED, application.state());
        assertNotNull(application.context());
    }

    @Test
    void buildAnnouncesDholeAndTheApplicationReportsToTheSameOutput() {
        Application application = Bootstrap.create(BootstrapTest.class).build();
        application.start();
        application.stop();

        assertEquals(
                List.of("Dhole", "", "Application starting...", "Application ready.",
                        "Application stopped."),
                outputLines());
    }

    @Test
    void applicationClassIsRequired() {
        assertThrows(NullPointerException.class, () -> Bootstrap.create(null));
    }

    private List<String> outputLines() {
        return captured.toString(StandardCharsets.UTF_8).lines().toList();
    }
}
