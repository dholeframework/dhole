package org.dhole.internal.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import org.dhole.config.ConfigurationException;

/**
 * Resolves typed environment values for {@link org.dhole.env.Env} while settings are configured.
 *
 * <p>A resolver is bound to the current thread only for the duration of {@link #within}, and the
 * binding is always removed afterwards. Every resolved value is recorded with its source.
 *
 * <p>Rules: an environment value (process, then {@code .env}) wins over a default; a missing value
 * without default fails; malformed typed values fail; in production a secret may not fall back to
 * a default. Messages never contain secret values.
 */
public final class EnvResolver {

    private static final ThreadLocal<EnvResolver> CURRENT = new ThreadLocal<>();

    private final DefaultEnvironment environment;
    private final Map<String, ResolvedValue> resolved = new LinkedHashMap<>();
    private String section;

    EnvResolver(DefaultEnvironment environment) {
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    /**
     * Returns the resolver bound to the current thread.
     *
     * @throws IllegalStateException when no settings are being configured on this thread
     */
    public static EnvResolver current() {
        EnvResolver resolver = CURRENT.get();
        if (resolver == null) {
            throw new IllegalStateException(
                    "Environment values can only be read while Settings.configure(SettingsBuilder) runs");
        }
        return resolver;
    }

    <T> T within(Supplier<T> action) {
        EnvResolver previous = CURRENT.get();
        CURRENT.set(this);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /**
     * Runs {@code action} with {@code section} named as the requirer in configuration errors.
     */
    void inSection(String section, Runnable action) {
        String previous = this.section;
        this.section = section;
        try {
            action.run();
        } finally {
            this.section = previous;
        }
    }

    public String required(String name) {
        Objects.requireNonNull(name, "name");
        return lookup(name).orElseThrow(() -> failure(
                "Missing required environment variable:\n" + name));
    }

    public String withDefault(String name, String defaultValue) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(defaultValue, "defaultValue");
        return lookup(name).orElseGet(() -> useDefault(name, defaultValue));
    }

    public int integer(String name, int defaultValue) {
        Objects.requireNonNull(name, "name");
        return lookup(name)
                .map(value -> convert(name, value, "integer", Integer::parseInt))
                .orElseGet(() -> Integer.parseInt(useDefault(name, Integer.toString(defaultValue))));
    }

    public boolean bool(String name, boolean defaultValue) {
        Objects.requireNonNull(name, "name");
        return lookup(name)
                .map(value -> convert(name, value, "boolean", EnvResolver::parseBoolean))
                .orElseGet(() -> Boolean.parseBoolean(useDefault(name, Boolean.toString(defaultValue))));
    }

    /**
     * Returns every value resolved so far, in resolution order.
     */
    Map<String, ResolvedValue> resolved() {
        return Collections.unmodifiableMap(resolved);
    }

    private Optional<String> lookup(String name) {
        Optional<String> value = environment.get(name);
        value.ifPresent(v -> resolved.put(name,
                new ResolvedValue(v, environment.source(name).orElseThrow())));
        return value;
    }

    private String useDefault(String name, String defaultValue) {
        if (environment.isProduction() && SecretNames.isSecret(name)) {
            throw failure("Secret environment variable must be provided by the process environment "
                    + "in production:\n" + name + "\n\nDefault values are not allowed for secrets in production.");
        }
        resolved.put(name, new ResolvedValue(defaultValue, ValueSource.DEFAULT));
        return defaultValue;
    }

    private <T> T convert(String name, String value, String type, Function<String, T> parser) {
        try {
            return parser.apply(value);
        } catch (IllegalArgumentException e) {
            throw failure("Invalid " + type + " value for environment variable:\n" + name
                    + "\n\nvalue = " + SecretNames.display(name, value));
        }
    }

    private static boolean parseBoolean(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException("not a boolean");
        };
    }

    private ConfigurationException failure(String problem) {
        String requiredBy = section == null ? "" : "\n\nRequired by:\n" + section;
        return new ConfigurationException("Configuration Error\n\n" + problem + requiredBy);
    }

    record ResolvedValue(String value, ValueSource source) {

        /**
         * Never includes the value.
         */
        @Override
        public String toString() {
            return "ResolvedValue[source=" + source + "]";
        }
    }
}
