package org.dhole.internal.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.dhole.application.ApplicationState;
import org.junit.jupiter.api.Test;

class ApplicationBuilderTest {

    @Test
    void buildsACreatedApplicationWithAContext() {
        DefaultApplication application = ApplicationBuilder.create().build();

        assertEquals(ApplicationState.CREATED, application.state());
        assertNotNull(application.context());
    }

    @Test
    void everyBuildCreatesAnIndependentApplication() {
        ApplicationBuilder builder = ApplicationBuilder.create()
                .output(new PrintStream(OutputStream.nullOutputStream()));

        DefaultApplication first = builder.build();
        DefaultApplication second = builder.build();
        first.start();

        assertNotSame(first.context(), second.context());
        assertEquals(ApplicationState.RUNNING, first.state());
        assertEquals(ApplicationState.CREATED, second.state());
    }

    @Test
    void lifecycleProgressIsReportedToTheConfiguredOutput() {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        DefaultApplication application = ApplicationBuilder.create()
                .output(new PrintStream(captured, true, StandardCharsets.UTF_8))
                .build();

        application.start();
        application.stop();

        assertEquals(
                List.of("Application starting...", "Application ready.", "Application stopped."),
                captured.toString(StandardCharsets.UTF_8).lines().toList());
    }

    @Test
    void outputIsRequired() {
        assertThrows(NullPointerException.class, () -> ApplicationBuilder.create().output(null));
    }
}
