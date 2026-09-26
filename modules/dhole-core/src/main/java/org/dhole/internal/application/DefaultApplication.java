package org.dhole.internal.application;

import java.util.Objects;

import org.dhole.application.Application;
import org.dhole.application.ApplicationContext;
import org.dhole.application.ApplicationState;
import org.dhole.internal.lifecycle.LifecycleManager;

/**
 * Default {@link Application} implementation.
 *
 * <p>Lifecycle behaviour is delegated to the application's {@link LifecycleManager}.
 */
public final class DefaultApplication implements Application {

    private final ApplicationContext context;
    private final LifecycleManager lifecycle;

    DefaultApplication(ApplicationContext context, LifecycleManager lifecycle) {
        this.context = Objects.requireNonNull(context, "context");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    }

    @Override
    public void start() {
        lifecycle.start();
    }

    @Override
    public void stop() {
        lifecycle.stop();
    }

    @Override
    public ApplicationContext context() {
        return context;
    }

    @Override
    public ApplicationState state() {
        return lifecycle.state();
    }
}
