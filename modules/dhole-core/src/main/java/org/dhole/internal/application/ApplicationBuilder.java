package org.dhole.internal.application;

import java.io.PrintStream;
import java.util.Objects;

import org.dhole.internal.lifecycle.LifecycleManager;

/**
 * Assembles the core runtime of one application: its context and its lifecycle.
 *
 * <p>Every {@link #build()} call creates a new, independent application.
 */
final class ApplicationBuilder {

    private PrintStream output = System.out;

    private ApplicationBuilder() {
    }

    static ApplicationBuilder create() {
        return new ApplicationBuilder();
    }

    /**
     * Sets where lifecycle progress is reported. Defaults to {@link System#out}.
     */
    ApplicationBuilder output(PrintStream output) {
        this.output = Objects.requireNonNull(output, "output");
        return this;
    }

    DefaultApplication build() {
        return new DefaultApplication(new DefaultApplicationContext(), new LifecycleManager(output));
    }
}
