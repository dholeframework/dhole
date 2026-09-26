package org.dhole.internal.compiler;

import java.util.Objects;

/**
 * A problem found by the metadata compiler.
 *
 * @param code the stable code, or {@code null} when the problem has none
 */
record Diagnostic(DiagnosticCode code, DiagnosticSeverity severity, String message) {

    Diagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
    }

    /**
     * Formats the diagnostic as {@code Dhole Error DHOLE-META-001: message}.
     */
    String format() {
        String kind = severity == DiagnosticSeverity.ERROR ? "Dhole Error" : "Dhole Warning";
        return kind + (code == null ? "" : " " + code.code()) + ": " + message;
    }
}
