package org.dhole.internal.lifecycle;

import java.io.PrintStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.dhole.application.ApplicationState;

/**
 * Drives the lifecycle state of one application and reports its progress.
 *
 * <p>Transitions are claimed atomically, so concurrent calls cannot both succeed.
 */
public final class LifecycleManager {

    private final PrintStream output;
    private final AtomicReference<ApplicationState> state =
            new AtomicReference<>(ApplicationState.CREATED);

    public LifecycleManager(PrintStream output) {
        this.output = Objects.requireNonNull(output, "output");
    }

    /**
     * Moves the application from {@code CREATED} through {@code STARTING} to {@code RUNNING}.
     *
     * @throws IllegalStateException if the application is not {@code CREATED}
     */
    public void start() {
        transition("start", ApplicationState.CREATED, ApplicationState.STARTING);
        output.println("Application starting...");
        state.set(ApplicationState.RUNNING);
        output.println("Application ready.");
    }

    /**
     * Moves the application from {@code RUNNING} through {@code STOPPING} to {@code STOPPED}.
     *
     * @throws IllegalStateException if the application is not {@code RUNNING}
     */
    public void stop() {
        transition("stop", ApplicationState.RUNNING, ApplicationState.STOPPING);
        completeStop();
    }

    /**
     * Stops the application if it is {@code RUNNING}; does nothing in any other state.
     *
     * <p>Used on JVM shutdown, where the application may never have started or may already be
     * stopped, and neither case is an error.
     */
    public void stopIfRunning() {
        if (state.compareAndSet(ApplicationState.RUNNING, ApplicationState.STOPPING)) {
            completeStop();
        }
    }

    public ApplicationState state() {
        return state.get();
    }

    private void completeStop() {
        state.set(ApplicationState.STOPPED);
        output.println("Application stopped.");
    }

    private void transition(String operation, ApplicationState expected, ApplicationState next) {
        if (!state.compareAndSet(expected, next)) {
            throw new IllegalStateException(
                    "Cannot " + operation + " application in state " + state.get()
                            + "; expected " + expected);
        }
    }
}
