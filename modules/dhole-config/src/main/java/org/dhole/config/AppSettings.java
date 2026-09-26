package org.dhole.config;

/**
 * Validated application settings.
 *
 * @param name the application name
 * @param port the application port
 */
public record AppSettings(String name, int port) {
}
