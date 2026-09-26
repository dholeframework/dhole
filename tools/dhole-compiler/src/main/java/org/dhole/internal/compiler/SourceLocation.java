package org.dhole.internal.compiler;

import java.util.Objects;

/**
 * Portable source position: the path relative to the source root (for example
 * {@code com/acme/shop/UserService.java}) and a 1-based line.
 */
record SourceLocation(String path, long line) {

    SourceLocation {
        Objects.requireNonNull(path, "path");
        if (line < 1) {
            throw new IllegalArgumentException("line must be positive");
        }
    }

    @Override
    public String toString() {
        return path + ":" + line;
    }
}
