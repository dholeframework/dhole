package org.dhole;

import java.util.Objects;

import org.dhole.internal.application.DefaultApplication;
import org.dhole.internal.bootstrap.Bootstrap;

/**
 * Entry point of a Dhole application.
 *
 * <pre>{@code
 * public static void main(String[] args) {
 *     Dhole.run(App.class);
 * }
 * }</pre>
 */
public final class Dhole {

    private Dhole() {
    }

    /**
     * Bootstraps and starts the application identified by the given class.
     *
     * <p>The package of {@code applicationClass} is the application root. A JVM shutdown hook
     * stops the application when the JVM terminates.
     *
     * @param applicationClass the application class, usually the one declaring {@code main}
     */
    public static void run(Class<?> applicationClass) {
        Objects.requireNonNull(applicationClass, "applicationClass");
        DefaultApplication application = Bootstrap.create(applicationClass).build();
        Runtime.getRuntime().addShutdownHook(new Thread(application::shutdown, "dhole-shutdown"));
        application.start();
    }
}
