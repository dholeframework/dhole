package org.dhole.config;

/**
 * Configures {@link AppSettings}.
 */
public interface AppSettingsBuilder {

    /**
     * Sets the application name; must not be blank.
     */
    AppSettingsBuilder name(String name);

    /**
     * Sets the application port, from {@code 0} to {@code 65535}.
     */
    AppSettingsBuilder port(int port);
}
