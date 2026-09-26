package org.dhole.internal.config;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.dhole.config.Environment;
import org.dhole.config.SettingsRegistry;
import org.dhole.internal.config.EnvResolver.ResolvedValue;

/**
 * Result of a successful configuration load: environment, validated settings and every
 * environment value the configuration read.
 */
final class LoadedConfiguration {

    private final Environment environment;
    private final SettingsRegistry settings;
    private final Map<String, ResolvedValue> values;

    LoadedConfiguration(Environment environment, SettingsRegistry settings, Map<String, ResolvedValue> values) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.values = new TreeMap<>(values);
    }

    Environment environment() {
        return environment;
    }

    SettingsRegistry settings() {
        return settings;
    }

    Map<String, ResolvedValue> values() {
        return Map.copyOf(values);
    }

    /**
     * Renders the values read by the configuration, sorted by name, with secrets masked:
     *
     * <pre>
     * APP_NAME       Shop
     * DB_PASSWORD    ********
     * </pre>
     */
    String report() {
        int width = values.keySet().stream().mapToInt(String::length).max().orElse(0) + 4;
        return values.entrySet().stream()
                .map(entry -> padRight(entry.getKey(), width)
                        + SecretNames.display(entry.getKey(), entry.getValue().value()))
                .collect(Collectors.joining("\n"));
    }

    /**
     * Never includes values.
     */
    @Override
    public String toString() {
        return "LoadedConfiguration[environment=" + environment.name() + ", values=" + values.keySet() + "]";
    }

    private static String padRight(String text, int width) {
        return text + " ".repeat(width - text.length());
    }
}
