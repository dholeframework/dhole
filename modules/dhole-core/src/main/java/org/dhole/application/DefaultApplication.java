package org.dhole.application;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default {@link Application} implementation.
 *
 * <p>Lifecycle transitions are claimed atomically, so concurrent calls cannot both succeed.
 */
final class DefaultApplication implements Application {

    private final ApplicationContext context;
    private final AtomicReference<ApplicationState> state =
            new AtomicReference<>(ApplicationState.CREATED);

    DefaultApplication(ApplicationContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

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
    public ApplicationContext context() {
        return context;
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
