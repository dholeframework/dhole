package org.dhole.config;

/**
 * Invalid, missing or malformed application configuration.
 *
 * <p>Messages never contain the value of a secret.
 */
public class ConfigurationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
