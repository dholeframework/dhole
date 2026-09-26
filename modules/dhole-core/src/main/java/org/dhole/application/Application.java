package org.dhole.application;

/**
 * A running Dhole application instance.
 *
 * <p>An application owns its lifecycle state. Invalid lifecycle transitions are rejected.
 */
public interface Application {

    /**
     * Starts the application.
     */
    void start();

    /**
     * Stops the application.
     */
    void stop();

    /**
     * Returns the context owned by this application.
     *
     * @return the application context, never {@code null}
     */
    ApplicationContext context();

    /**
     * Returns the current lifecycle state.
     *
     * @return the current state, never {@code null}
     */
    ApplicationState state();
}
