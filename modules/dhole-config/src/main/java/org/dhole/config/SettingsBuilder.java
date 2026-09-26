package org.dhole.config;

import java.util.function.Consumer;

/**
 * Declares application configuration from {@code Settings.configure(SettingsBuilder)}.
 *
 * <pre>{@code
 * public final class Settings {
 *
 *     public static void configure(SettingsBuilder settings) {
 *         settings.app(app -> app
 *             .name(env("APP_NAME", "Hello"))
 *             .port(envInt("APP_PORT", 8080))
 *         );
 *     }
 * }
 * }</pre>
 */
public interface SettingsBuilder {

    /**
     * Returns the environment being configured, so configuration can react to it.
     */
    Environment environment();

    /**
     * Configures the application settings. May be called more than once; later values win.
     */
    void app(Consumer<AppSettingsBuilder> app);
}
