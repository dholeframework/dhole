package org.dhole.internal.bootstrap;

import java.io.PrintStream;
import java.util.Objects;

import org.dhole.internal.application.ApplicationBuilder;
import org.dhole.internal.application.DefaultApplication;
import org.dhole.internal.module.ModuleIndex;
import org.dhole.internal.module.ModuleRuntime;

/**
 * Prepares an application from its application class.
 *
 * <p>Assembles the core runtime (context and lifecycle) and the modules named by the application's
 * build-generated {@code META-INF/dhole/modules.idx}, read through the application class's loader.
 * The module graph and every activator are validated here, before the application can start.
 * Later bootstrap stages (plugins) are added by their milestones.
 */
public final class Bootstrap {

    private final Class<?> applicationClass;
    private final PrintStream output;

    private Bootstrap(Class<?> applicationClass, PrintStream output) {
        this.applicationClass = applicationClass;
        this.output = output;
    }

    /**
     * Creates the bootstrap for the given application class, reporting to {@link System#out}.
     *
     * @param applicationClass the class identifying the application root
     */
    public static Bootstrap create(Class<?> applicationClass) {
        return create(applicationClass, System.out);
    }

    public static Bootstrap create(Class<?> applicationClass, PrintStream output) {
        Objects.requireNonNull(applicationClass, "applicationClass");
        Objects.requireNonNull(output, "output");
        return new Bootstrap(applicationClass, output);
    }

    /**
     * Assembles a new application in state {@code CREATED}.
     *
     * @throws IllegalStateException if the module index, graph or an activator is invalid
     */
    public DefaultApplication build() {
        output.println("Dhole");
        output.println();
        ClassLoader loader = applicationClass.getClassLoader();
        ModuleRuntime modules = loader == null
                ? ModuleRuntime.none()
                : ModuleRuntime.of(ModuleIndex.load(loader), loader);
        return ApplicationBuilder.create().output(output).modules(applicationClass, modules).build();
    }
}
