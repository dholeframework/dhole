package org.dhole.internal.config;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

import org.dhole.config.AppSettings;
import org.dhole.config.Environment;
import org.dhole.config.SettingsRegistry;

/**
 * The loaded configuration of an application, for framework modules and tooling: environment,
 * validated settings and the masked report of the values read.
 *
 * <p>Public only for other Dhole modules and the CLI; not application API.
 */
public final class Configuration {

    private final LoadedConfiguration loaded;

    private Configuration(LoadedConfiguration loaded) {
        this.loaded = loaded;
    }

    /**
     * Loads from the real process environment and the {@code .env} of the working directory.
     *
     * @throws org.dhole.config.ConfigurationException if the configuration is missing or invalid
     */
    public static Configuration load(Class<?> applicationClass) {
        return load(applicationClass, System.getenv(), Path.of(""));
    }

    /**
     * Loads from the given process environment and the {@code .env} of {@code directory}.
     *
     * @throws org.dhole.config.ConfigurationException if the configuration is missing or invalid
     */
    public static Configuration load(Class<?> applicationClass, Map<String, String> process, Path directory) {
        Objects.requireNonNull(applicationClass, "applicationClass");
        return new Configuration(ConfigurationLoader.load(applicationClass, EnvironmentLoader.load(process, directory)));
    }

    public Environment environment() {
        return loaded.environment();
    }

    public SettingsRegistry settings() {
        return loaded.settings();
    }

    public AppSettings app() {
        return loaded.settings().app();
    }

    /**
     * The values read by the configuration, sorted by name, secrets masked.
     */
    public String report() {
        return loaded.report();
    }

    @Override
    public String toString() {
        return loaded.toString();
    }
}
