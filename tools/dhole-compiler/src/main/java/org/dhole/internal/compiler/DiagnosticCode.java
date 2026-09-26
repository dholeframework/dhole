package org.dhole.internal.compiler;

/**
 * Stable, machine-readable codes of metadata compiler diagnostics.
 *
 * <p>Dependency codes DHOLE-DI-001 (missing), DHOLE-DI-002 (ambiguous) and DHOLE-DI-003
 * (circular) are reported by the dependency container, which owns dependency semantics.
 */
enum DiagnosticCode {

    /**
     * The configured application class is malformed or does not exist.
     */
    INVALID_APPLICATION("DHOLE-META-001");

    private final String code;

    DiagnosticCode(String code) {
        this.code = code;
    }

    String code() {
        return code;
    }
}
