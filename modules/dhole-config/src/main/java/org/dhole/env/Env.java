package org.dhole.env;

import org.dhole.internal.config.EnvResolver;

/**
 * Typed access to environment variables from {@code Settings.configure(SettingsBuilder)}.
 *
 * <pre>{@code
 * import static org.dhole.env.Env.*;
 *
 * settings.app(app -> app
 *     .name(env("APP_NAME", "Hello"))
 *     .port(envInt("APP_PORT", 8080))
 * );
 * }</pre>
 *
 * <p>Values come from the process environment first, then {@code .env} (never in production),
 * then the given default. An empty value counts as absent. Failures throw
 * {@link org.dhole.config.ConfigurationException}; secret values are never included in messages.
 * These methods may only be called while settings are being configured.
 */
public final class Env {

    private Env() {
    }

    /**
     * Returns a required value.
     *
     * @throws org.dhole.config.ConfigurationException if the variable is missing
     */
    public static String env(String name) {
        return EnvResolver.current().required(name);
    }

    /**
     * Returns a value, or {@code defaultValue} when the variable is missing.
     *
     * @throws org.dhole.config.ConfigurationException if the variable is a secret, the environment
     *         is production and the default would be used
     */
    public static String env(String name, String defaultValue) {
        return EnvResolver.current().withDefault(name, defaultValue);
    }

    /**
     * Returns an integer value, or {@code defaultValue} when the variable is missing.
     *
     * @throws org.dhole.config.ConfigurationException if the value is not a valid integer
     */
    public static int envInt(String name, int defaultValue) {
        return EnvResolver.current().integer(name, defaultValue);
    }

    /**
     * Returns a boolean value ({@code true} or {@code false}, case-insensitive), or
     * {@code defaultValue} when the variable is missing.
     *
     * @throws org.dhole.config.ConfigurationException if the value is not {@code true} or
     *         {@code false}
     */
    public static boolean envBool(String name, boolean defaultValue) {
        return EnvResolver.current().bool(name, defaultValue);
    }
}
