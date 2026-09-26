package org.dhole.internal.compiler;

import java.util.Objects;

/**
 * The application class configured with {@code -Adhole.application} and its root package.
 * Only types in the root package and its subpackages are application code.
 */
record ApplicationRoot(String applicationClass, String rootPackage) {

    ApplicationRoot {
        Objects.requireNonNull(applicationClass, "applicationClass");
        Objects.requireNonNull(rootPackage, "rootPackage");
    }

    boolean contains(String packageName) {
        return rootPackage.isEmpty()
                ? packageName.isEmpty()
                : packageName.equals(rootPackage) || packageName.startsWith(rootPackage + ".");
    }
}
