package org.dhole.internal.bootstrap;

import java.io.PrintStream;
import java.util.Objects;

import org.dhole.application.Application;
import org.dhole.internal.application.ApplicationBuilder;

/**
 * Prepares an application from its application class.
 *
 * <p>In M1 the bootstrap assembles only the core runtime (context and lifecycle). Later bootstrap
 * stages (environment, settings, metadata, plugins) are added by their milestones.
 */
public final class Bootstrap {

    private final PrintStream output;

    private Bootstrap(PrintStream output) {
        this.output = output;
    }

    /**
     * Creates the bootstrap for the given application class.
     *
     * @param applicationClass the class identifying the application root
     */
    public static Bootstrap create(Class<?> applicationClass) {
        Objects.requireNonNull(applicationClass, "applicationClass");
        return new Bootstrap(System.out);
    }

    /**
     * Assembles a new application in state {@code CREATED}.
     */
    public Application build() {
        output.println("Dhole");
        output.println();
        return ApplicationBuilder.create().output(output).build();
    }
}
