package org.dhole.serialization;

/**
 * A value cannot be serialized, or input cannot be deserialized into the requested type: malformed
 * input, incompatible types, unknown or missing properties. Messages never contain input values.
 */
public class SerializationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SerializationException(String message) {
        super(message);
    }

    public SerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
