package org.dhole.internal.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.dhole.web.BindingException;

/**
 * A path, query or header value is missing or not convertible; answered with {@code 400
 * INVALID_PARAMETER} and details naming the parameter, its source and the expected type, never the
 * received value.
 *
 * <p>Public only for the binding wrappers in {@code org.dhole.web}; not application API.
 */
public final class InvalidParameterException extends BindingException {

    private static final long serialVersionUID = 1L;

    private final String parameter;
    private final String source;
    private final String expected;

    private InvalidParameterException(String message, String parameter, String source, String expected, Throwable cause) {
        super(message, cause);
        this.parameter = parameter;
        this.source = source;
        this.expected = expected;
    }

    /**
     * @param source {@code path}, {@code query} or {@code header}
     */
    public static InvalidParameterException missing(String parameter, String source) {
        Objects.requireNonNull(parameter, "parameter");
        return new InvalidParameterException("Missing " + source + " parameter '" + parameter + "'.", parameter, source,
                null, null);
    }

    static InvalidParameterException invalid(String parameter, String source, String expected, Throwable cause) {
        return new InvalidParameterException("Invalid " + source + " parameter '" + parameter + "': expected "
                + expected + ".", parameter, source, expected, cause);
    }

    Map<String, Object> details() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("parameter", parameter);
        details.put("source", source);
        Optional.ofNullable(expected).ifPresent(value -> details.put("expected", value));
        return details;
    }
}
