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
    INVALID_APPLICATION("DHOLE-META-001"),

    /**
     * The effective path of a typed route cannot be determined at build time.
     */
    ROUTE_PATH_UNKNOWN("DHOLE-ROUTE-001"),

    /**
     * A typed route handler is not a method reference {@code this::method} of the controller.
     */
    ROUTE_HANDLER_UNSUPPORTED("DHOLE-ROUTE-002"),

    /**
     * The source of a handler parameter cannot be determined with certainty.
     */
    BIND_SOURCE_UNKNOWN("DHOLE-BIND-001"),

    /**
     * A path placeholder has no handler parameter, or a {@code Path<T>} parameter has no placeholder.
     */
    BIND_PATH_MISMATCH("DHOLE-BIND-002"),

    /**
     * More than one parameter claims the request body.
     */
    BIND_MULTIPLE_BODIES("DHOLE-BIND-003"),

    /**
     * A parameter or response type is not supported for its source.
     */
    BIND_TYPE_UNSUPPORTED("DHOLE-BIND-004");

    private final String code;

    DiagnosticCode(String code) {
        this.code = code;
    }

    String code() {
        return code;
    }
}
