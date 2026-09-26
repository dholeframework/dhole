package org.dhole.internal.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.dhole.config.ConfigurationException;

/**
 * Loads the {@link DefaultEnvironment} from the process environment and an optional {@code .env}.
 *
 * <p>Rules:
 * <ul>
 *   <li>the environment name comes from {@code APP_ENV}: process environment first, then
 *       {@code .env}, otherwise {@code development};</li>
 *   <li>{@code APP_ENV} must be exactly {@code development}, {@code test} or {@code production};</li>
 *   <li>in {@code production}, {@code .env} is not read at all, so production can only be selected
 *       by the process environment;</li>
 *   <li>a missing {@code .env} is not an error.</li>
 * </ul>
 */
final class EnvironmentLoader {

    static final String ENVIRONMENT_VARIABLE = "APP_ENV";
    static final String DEFAULT_ENVIRONMENT = "development";

    private static final List<String> ENVIRONMENTS = List.of("development", "test", "production");
    private static final String DOT_ENV = ".env";

    private EnvironmentLoader() {
    }

    /**
     * Loads from the real process environment and the {@code .env} of the working directory.
     */
    static DefaultEnvironment load() {
        return load(System.getenv(), Path.of(""));
    }

    static DefaultEnvironment load(Map<String, String> process, Path directory) {
        Objects.requireNonNull(process, "process");
        Objects.requireNonNull(directory, "directory");

        Optional<String> processName = nonEmpty(process.get(ENVIRONMENT_VARIABLE));
        processName.ifPresent(EnvironmentLoader::requireKnown);
        if (processName.filter("production"::equals).isPresent()) {
            return new DefaultEnvironment("production", process, Map.of());
        }

        Map<String, String> dotEnv = readDotEnv(directory.resolve(DOT_ENV));
        String name = processName.orElseGet(() -> nameFromDotEnv(dotEnv));
        return new DefaultEnvironment(name, process, dotEnv);
    }

    private static String nameFromDotEnv(Map<String, String> dotEnv) {
        Optional<String> name = nonEmpty(dotEnv.get(ENVIRONMENT_VARIABLE));
        name.ifPresent(EnvironmentLoader::requireKnown);
        if (name.filter("production"::equals).isPresent()) {
            throw new ConfigurationException(
                    "Configuration Error\n\nAPP_ENV=production must be set in the process environment.\n"
                            + ".env files are not used in production.");
        }
        return name.orElse(DEFAULT_ENVIRONMENT);
    }

    private static void requireKnown(String name) {
        if (!ENVIRONMENTS.contains(name)) {
            throw new ConfigurationException(
                    "Configuration Error\n\nInvalid environment variable:\nAPP_ENV\n\nvalue = " + name
                            + "\nexpected one of: development, test, production");
        }
    }

    private static Map<String, String> readDotEnv(Path file) {
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }
        try {
            return DotEnvParser.parse(Files.readAllLines(file, StandardCharsets.UTF_8), DOT_ENV);
        } catch (IOException e) {
            throw new ConfigurationException("Configuration Error\n\nUnable to read " + DOT_ENV, e);
        }
    }

    private static Optional<String> nonEmpty(String value) {
        return Optional.ofNullable(value).filter(v -> !v.isEmpty());
    }
}
