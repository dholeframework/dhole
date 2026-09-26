package org.dhole.internal.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.dhole.config.AppSettings;
import org.dhole.config.AppSettingsBuilder;
import org.dhole.config.ConfigurationException;
import org.dhole.config.Environment;
import org.dhole.config.SettingsBuilder;
import org.dhole.config.SettingsRegistry;

/**
 * Collects settings declared by {@code Settings.configure} and validates them into a registry.
 *
 * <p>Defaults when not configured: application name = simple name of the application class,
 * port = {@code 8080}.
 */
final class DefaultSettingsBuilder implements SettingsBuilder {

    static final int DEFAULT_PORT = 8080;
    private static final int MAX_PORT = 65535;

    private final Environment environment;
    private final EnvResolver resolver;
    private String name;
    private int port = DEFAULT_PORT;

    DefaultSettingsBuilder(Environment environment, EnvResolver resolver, String defaultName) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.name = Objects.requireNonNull(defaultName, "defaultName");
    }

    @Override
    public Environment environment() {
        return environment;
    }

    @Override
    public void app(Consumer<AppSettingsBuilder> app) {
        Objects.requireNonNull(app, "app");
        resolver.inSection("App settings", () -> app.accept(new App()));
    }

    /**
     * Validates the collected settings.
     *
     * @throws ConfigurationException listing every invalid setting
     */
    SettingsRegistry build() {
        List<String> problems = new ArrayList<>();
        if (name.isBlank()) {
            problems.add("app.name must not be blank");
        }
        if (port < 0 || port > MAX_PORT) {
            problems.add("app.port must be between 0 and " + MAX_PORT + "\n\nport = " + port);
        }
        if (!problems.isEmpty()) {
            throw new ConfigurationException("Configuration Error\n\n" + String.join("\n\n", problems));
        }
        return new Registry(new AppSettings(name, port));
    }

    private final class App implements AppSettingsBuilder {

        @Override
        public AppSettingsBuilder name(String name) {
            DefaultSettingsBuilder.this.name = Objects.requireNonNull(name, "name");
            return this;
        }

        @Override
        public AppSettingsBuilder port(int port) {
            DefaultSettingsBuilder.this.port = port;
            return this;
        }
    }

    private record Registry(AppSettings app) implements SettingsRegistry {
    }
}
