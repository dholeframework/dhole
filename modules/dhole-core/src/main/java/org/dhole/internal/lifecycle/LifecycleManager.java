package org.dhole.internal.lifecycle;

import java.io.PrintStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.dhole.application.ApplicationState;
import org.dhole.internal.module.ActivationContext;
import org.dhole.internal.module.ModuleRuntime;

/**
 * Drives the lifecycle state of one application and reports its progress.
 *
 * <p>Transitions are claimed atomically, so concurrent calls cannot both succeed. Starting starts
 * the application's modules; if one fails, the started ones are rolled back and the application
 * ends {@code FAILED}. Stopping stops the modules in reverse order.
 */
public final class LifecycleManager {

    private final PrintStream output;
    private final ModuleRuntime modules;
    private final ActivationContext context;
    private final AtomicReference<ApplicationState> state =
            new AtomicReference<>(ApplicationState.CREATED);

    public LifecycleManager(PrintStream output) {
        this(output, ModuleRuntime.none(), null);
    }

    /**
     * @param context what the modules receive; may be {@code null} only without modules
     */
    public LifecycleManager(PrintStream output, ModuleRuntime modules, ActivationContext context) {
        this.output = Objects.requireNonNull(output, "output");
        this.modules = Objects.requireNonNull(modules, "modules");
        if (context == null && !modules.ids().isEmpty()) {
            throw new IllegalArgumentException("Modules need an activation context");
        }
        this.context = context;
    }

    /**
     * Moves the application from {@code CREATED} through {@code STARTING} to {@code RUNNING}, starting
     * its modules in activation order.
     *
     * @throws IllegalStateException if the application is not {@code CREATED}
     * @throws org.dhole.internal.module.ModuleStartupException if a module fails; the application
     *         is then {@code FAILED} and the modules already started have been stopped
     */
    public void start() {
        transition("start", ApplicationState.CREATED, ApplicationState.STARTING);
        output.println("Application starting...");
        if (!modules.ids().isEmpty()) {
            try {
                modules.start(context);
            } catch (RuntimeException e) {
                state.set(ApplicationState.FAILED);
                output.println("Application failed to start.");
                throw e;
            }
        }
        state.set(ApplicationState.RUNNING);
        output.println("Application ready.");
    }

    /**
     * Moves the application from {@code RUNNING} through {@code STOPPING} to {@code STOPPED}.
     *
     * @throws IllegalStateException if the application is not {@code RUNNING}, or if a module fails
     *         to stop (the application still ends {@code STOPPED})
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
        try {
            modules.stop();
        } finally {
            state.set(ApplicationState.STOPPED);
            output.println("Application stopped.");
        }
    }

    private void transition(String operation, ApplicationState expected, ApplicationState next) {
        if (!state.compareAndSet(expected, next)) {
            throw new IllegalStateException(
                    "Cannot " + operation + " application in state " + state.get()
                            + "; expected " + expected);
        }
    }
}
