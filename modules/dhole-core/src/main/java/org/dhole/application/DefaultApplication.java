package org.dhole.application;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Default {@link Application} implementation.
 *
 * <p>Lifecycle transitions are claimed atomically, so concurrent calls cannot both succeed.
 */
final class DefaultApplication implements Application {

    private final AtomicReference<ApplicationState> state =
            new AtomicReference<>(ApplicationState.CREATED);

    @Override
    public void start() {
        transition("start", ApplicationState.CREATED, ApplicationState.STARTING);
        state.set(ApplicationState.RUNNING);
    }

    @Override
    public void stop() {
        transition("stop", ApplicationState.RUNNING, ApplicationState.STOPPING);
        state.set(ApplicationState.STOPPED);
    }

    @Override
    public ApplicationState state() {
        return state.get();
    }

    private void transition(String operation, ApplicationState expected, ApplicationState next) {
        if (!state.compareAndSet(expected, next)) {
            throw new IllegalStateException(
                    "Cannot " + operation + " application in state " + state.get()
                            + "; expected " + expected);
        }
    }
}
