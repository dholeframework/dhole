package org.dhole;

import java.util.Objects;

import org.dhole.application.Application;
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
     * <p>The package of {@code applicationClass} is the application root.
     *
     * @param applicationClass the application class, usually the one declaring {@code main}
     */
    public static void run(Class<?> applicationClass) {
        Objects.requireNonNull(applicationClass, "applicationClass");
        Application application = Bootstrap.create(applicationClass).build();
        application.start();
    }
}
