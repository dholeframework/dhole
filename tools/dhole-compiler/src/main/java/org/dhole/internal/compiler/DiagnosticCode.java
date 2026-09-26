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
    BIND_TYPE_UNSUPPORTED("DHOLE-BIND-004"),

    /**
     * {@code rules()} of a {@code Validatable} type is missing or not in the supported shape.
     */
    VAL_SHAPE("DHOLE-VAL-001"),

    /**
     * A {@code field(...)} argument is not a method reference to an accessor of the validated type.
     */
    VAL_FIELD("DHOLE-VAL-002"),

    /**
     * A rule is incompatible with the type of its field.
     */
    VAL_RULE_TYPE("DHOLE-VAL-003"),

    /**
     * {@code nested()} or {@code eachNested()} is applied to a type that is not {@code Validatable}.
     */
    VAL_NESTED("DHOLE-VAL-004"),

    /**
     * A field is declared more than once in {@code rules()}.
     */
    VAL_DUPLICATE_FIELD("DHOLE-VAL-005");

    private final String code;

    DiagnosticCode(String code) {
        this.code = code;
    }

    String code() {
        return code;
    }
}
