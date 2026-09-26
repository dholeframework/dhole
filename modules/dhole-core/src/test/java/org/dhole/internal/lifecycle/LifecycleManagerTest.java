package org.dhole.internal.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.dhole.application.ApplicationState;
import org.junit.jupiter.api.Test;

class LifecycleManagerTest {

    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
    private final LifecycleManager lifecycle =
            new LifecycleManager(new PrintStream(captured, true, StandardCharsets.UTF_8));

    @Test
    void newLifecycleIsCreatedAndSilent() {
        assertEquals(ApplicationState.CREATED, lifecycle.state());
        assertEquals(List.of(), outputLines());
    }

    @Test
    void startReportsProgressAndReachesRunning() {
        lifecycle.start();

        assertEquals(ApplicationState.RUNNING, lifecycle.state());
        assertEquals(List.of("Application starting...", "Application ready."), outputLines());
    }

    @Test
    void stopReportsProgressAndReachesStopped() {
        lifecycle.start();

        lifecycle.stop();

        assertEquals(ApplicationState.STOPPED, lifecycle.state());
        assertEquals(
                List.of("Application starting...", "Application ready.", "Application stopped."),
                outputLines());
    }

    @Test
    void rejectedStartNamesOperationAndCurrentState() {
        lifecycle.start();

        IllegalStateException failure = assertThrows(IllegalStateException.class, lifecycle::start);

        assertEquals("Cannot start application in state RUNNING; expected CREATED",
                failure.getMessage());
        assertEquals(ApplicationState.RUNNING, lifecycle.state());
    }

    @Test
    void rejectedStopNamesOperationAndCurrentStateAndReportsNothing() {
        IllegalStateException failure = assertThrows(IllegalStateException.class, lifecycle::stop);

        assertEquals("Cannot stop application in state CREATED; expected RUNNING",
                failure.getMessage());
        assertEquals(ApplicationState.CREATED, lifecycle.state());
        assertEquals(List.of(), outputLines());
    }

    @Test
    void stopIfRunningStopsARunningApplication() {
        lifecycle.start();

        lifecycle.stopIfRunning();

        assertEquals(ApplicationState.STOPPED, lifecycle.state());
        assertEquals(
                List.of("Application starting...", "Application ready.", "Application stopped."),
                outputLines());
    }

    @Test
    void stopIfRunningDoesNothingBeforeStart() {
        lifecycle.stopIfRunning();

        assertEquals(ApplicationState.CREATED, lifecycle.state());
        assertEquals(List.of(), outputLines());
    }

    @Test
    void stopIfRunningDoesNothingAfterStop() {
        lifecycle.start();
        lifecycle.stop();

        lifecycle.stopIfRunning();

        assertEquals(ApplicationState.STOPPED, lifecycle.state());
        assertEquals(
                List.of("Application starting...", "Application ready.", "Application stopped."),
                outputLines());
    }

    @Test
    void lifecycleRequiresAnOutput() {
        assertThrows(NullPointerException.class, () -> new LifecycleManager(null));
    }

    private List<String> outputLines() {
        return captured.toString(StandardCharsets.UTF_8).lines().toList();
    }
}
