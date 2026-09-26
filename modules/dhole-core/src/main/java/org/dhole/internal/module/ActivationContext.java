package org.dhole.internal.module;

import java.io.PrintStream;
import java.util.Objects;

/**
 * What a module activator receives: the application class (whose class loader holds the
 * application's generated metadata) and where startup progress is reported.
 */
public final class ActivationContext {

    private final Class<?> applicationClass;
    private final PrintStream output;

    public ActivationContext(Class<?> applicationClass, PrintStream output) {
        this.applicationClass = Objects.requireNonNull(applicationClass, "applicationClass");
        this.output = Objects.requireNonNull(output, "output");
    }

    public Class<?> applicationClass() {
        return applicationClass;
    }

    public ClassLoader classLoader() {
        return applicationClass.getClassLoader();
    }

    public PrintStream output() {
        return output;
    }
}
