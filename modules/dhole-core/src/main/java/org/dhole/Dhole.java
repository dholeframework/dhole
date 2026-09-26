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

    private static final String VERBOSE = "dhole.verbose";

    private Dhole() {
    }

    /**
     * Bootstraps and starts the application identified by the given class, with the modules its
     * build activated.
     *
     * <p>The package of {@code applicationClass} is the application root. A JVM shutdown hook
     * stops the application when the JVM terminates. If startup fails, the modules already started
     * are stopped, the error is reported on standard error (with the stack trace when the system
     * property {@code dhole.verbose} is {@code true}) and the JVM exits with status 1.
     *
     * @param applicationClass the application class, usually the one declaring {@code main}
     */
    public static void run(Class<?> applicationClass) {
        Objects.requireNonNull(applicationClass, "applicationClass");
        try {
            DefaultApplication application = Bootstrap.create(applicationClass).build();
            Runtime.getRuntime().addShutdownHook(new Thread(application::shutdown, "dhole-shutdown"));
            application.start();
        } catch (RuntimeException | LinkageError failure) {
            System.err.println();
            System.err.println(failure.getMessage() == null ? failure.getClass().getName() : failure.getMessage());
            System.err.println();
            System.err.println("Application startup aborted.");
            if (Boolean.getBoolean(VERBOSE)) {
                failure.printStackTrace();
            }
            System.exit(1);
        }
    }
}
