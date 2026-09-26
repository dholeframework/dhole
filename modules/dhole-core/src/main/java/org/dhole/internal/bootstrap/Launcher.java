package org.dhole.internal.bootstrap;

import java.io.PrintStream;

import org.dhole.internal.application.DefaultApplication;

/**
 * Starts an application for tooling that owns the process, such as {@code dhole dev}: the same
 * bootstrap as {@code Dhole.run}, without a JVM shutdown hook and without exiting on failure.
 * Only JDK types cross this boundary, so tooling in another class loader can call it.
 *
 * <p>Internal; not an application entry point.
 */
public final class Launcher {

    private Launcher() {
    }

    /**
     * Bootstraps and starts the application.
     *
     * @return closing it stops the application
     * @throws RuntimeException if startup fails; started modules have been rolled back
     */
    public static AutoCloseable start(Class<?> applicationClass, PrintStream output) {
        DefaultApplication application = Bootstrap.create(applicationClass, output).build();
        application.start();
        return application::shutdown;
    }
}
