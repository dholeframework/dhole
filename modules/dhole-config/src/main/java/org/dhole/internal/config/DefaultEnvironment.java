package org.dhole.internal.config;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.dhole.config.Environment;

/**
 * Default {@link Environment}: process environment variables take precedence over {@code .env}.
 *
 * <p>A variable defined in the process environment is never replaced by {@code .env}, even when
 * its value is empty. Empty values are reported as absent.
 */
final class DefaultEnvironment implements Environment {

    enum Source {
        PROCESS,
        DOT_ENV
    }

    private final String name;
    private final Map<String, String> process;
    private final Map<String, String> dotEnv;

    DefaultEnvironment(String name, Map<String, String> process, Map<String, String> dotEnv) {
        this.name = Objects.requireNonNull(name, "name");
        this.process = Map.copyOf(process);
        this.dotEnv = Map.copyOf(dotEnv);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Optional<String> get(String key) {
        Objects.requireNonNull(key, "key");
        return source(key)
                .map(source -> source == Source.PROCESS ? process.get(key) : dotEnv.get(key))
                .filter(value -> !value.isEmpty());
    }

    /**
     * Returns where a variable is defined, if anywhere.
     */
    Optional<Source> source(String key) {
        if (process.containsKey(key)) {
            return Optional.of(Source.PROCESS);
        }
        if (dotEnv.containsKey(key)) {
            return Optional.of(Source.DOT_ENV);
        }
        return Optional.empty();
    }

    /**
     * Never includes variable values.
     */
    @Override
    public String toString() {
        return "Environment[name=" + name + "]";
    }
}
