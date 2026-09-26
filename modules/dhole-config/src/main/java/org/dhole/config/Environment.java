package org.dhole.config;

import java.util.Optional;

/**
 * Runtime environment of an application: its name and its external configuration values.
 *
 * <p>The name is one of {@code development}, {@code test} or {@code production}.
 */
public interface Environment {

    /**
     * Returns the environment name: {@code development}, {@code test} or {@code production}.
     */
    String name();

    default boolean isDevelopment() {
        return "development".equals(name());
    }

    default boolean isTest() {
        return "test".equals(name());
    }

    default boolean isProduction() {
        return "production".equals(name());
    }

    /**
     * Returns the value of an external configuration variable.
     *
     * @param key the variable name
     * @return the value, or empty when the variable is not defined or is empty
     */
    Optional<String> get(String key);
}
