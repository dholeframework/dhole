package org.dhole.internal.application;

import java.io.PrintStream;
import java.util.Objects;

import org.dhole.internal.lifecycle.LifecycleManager;
import org.dhole.internal.module.ActivationContext;
import org.dhole.internal.module.ModuleRuntime;

/**
 * Assembles the core runtime of one application: its context, its lifecycle and its modules.
 *
 * <p>Every {@link #build()} call creates a new, independent application.
 */
public final class ApplicationBuilder {

    private PrintStream output = System.out;
    private ModuleRuntime modules = ModuleRuntime.none();
    private Class<?> applicationClass;

    private ApplicationBuilder() {
    }

    public static ApplicationBuilder create() {
        return new ApplicationBuilder();
    }

    /**
     * Sets where lifecycle progress is reported. Defaults to {@link System#out}.
     */
    public ApplicationBuilder output(PrintStream output) {
        this.output = Objects.requireNonNull(output, "output");
        return this;
    }

    /**
     * Sets the modules started with the application. Defaults to none.
     */
    public ApplicationBuilder modules(Class<?> applicationClass, ModuleRuntime modules) {
        this.applicationClass = Objects.requireNonNull(applicationClass, "applicationClass");
        this.modules = Objects.requireNonNull(modules, "modules");
        return this;
    }

    public DefaultApplication build() {
        ActivationContext context = applicationClass == null ? null : new ActivationContext(applicationClass, output);
        return new DefaultApplication(new DefaultApplicationContext(), new LifecycleManager(output, modules, context));
    }
}
