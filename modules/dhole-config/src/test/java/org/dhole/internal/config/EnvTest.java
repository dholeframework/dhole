package org.dhole.internal.config;

import static org.dhole.env.Env.env;
import static org.dhole.env.Env.envBool;
import static org.dhole.env.Env.envInt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.dhole.config.ConfigurationException;
import org.dhole.internal.config.EnvResolver.ResolvedValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnvTest {

    @TempDir
    Path directory;

    @Test
    void requiredValueIsReturnedWhenPresent() {
        assertEquals("Shop", resolve(Map.of("APP_NAME", "Shop"), () -> env("APP_NAME")));
    }

    @Test
    void missingRequiredValueFailsAndNamesTheVariable() {
        ConfigurationException failure = assertThrows(ConfigurationException.class,
                () -> resolve(Map.of(), () -> env("JWT_SECRET")));

        assertEquals("Configuration Error\n\nMissing required environment variable:\nJWT_SECRET",
                failure.getMessage());
    }

    @Test
    void missingRequiredValueNamesTheRequiringSection() {
        EnvResolver resolver = resolver(Map.of());

        ConfigurationException failure = assertThrows(ConfigurationException.class,
                () -> resolver.within(() -> {
                    resolver.inSection("App settings", () -> env("APP_NAME"));
                    return null;
                }));

        assertTrue(failure.getMessage().endsWith("Required by:\nApp settings"), failure.getMessage());
    }

    @Test
    void emptyValueCountsAsMissing() {
        assertThrows(ConfigurationException.class, () -> resolve(Map.of("APP_NAME", ""), () -> env("APP_NAME")));
        assertEquals("Hello", resolve(Map.of("APP_NAME", ""), () -> env("APP_NAME", "Hello")));
    }

    @Test
    void defaultValueIsUsedWhenMissing() {
        assertEquals("Hello", resolve(Map.of(), () -> env("APP_NAME", "Hello")));
        assertEquals(8080, resolve(Map.of(), () -> envInt("APP_PORT", 8080)));
        assertEquals(false, resolve(Map.of(), () -> envBool("APP_DEBUG", false)));
    }

    @Test
    void environmentValueWinsOverDefault() {
        assertEquals("Shop", resolve(Map.of("APP_NAME", "Shop"), () -> env("APP_NAME", "Hello")));
        assertEquals(9000, resolve(Map.of("APP_PORT", "9000"), () -> envInt("APP_PORT", 8080)));
        assertEquals(true, resolve(Map.of("APP_DEBUG", "true"), () -> envBool("APP_DEBUG", false)));
    }

    @Test
    void dotEnvValueWinsOverDefaultAndProcessWinsOverDotEnv() throws IOException {
        writeDotEnv("APP_NAME=FromDotEnv", "APP_PORT=9000");
        EnvResolver resolver = new EnvResolver(EnvironmentLoader.load(Map.of("APP_PORT", "7000"), directory));

        assertEquals("FromDotEnv", resolver.within(() -> env("APP_NAME", "Hello")));
        assertEquals(7000, resolver.within(() -> envInt("APP_PORT", 8080)));
    }

    @Test
    void invalidIntegerFails() {
        ConfigurationException failure = assertThrows(ConfigurationException.class,
                () -> resolve(Map.of("APP_PORT", "80a"), () -> envInt("APP_PORT", 8080)));

        assertEquals("Configuration Error\n\nInvalid integer value for environment variable:\nAPP_PORT\n\nvalue = 80a",
                failure.getMessage());
    }

    @Test
    void integerOutOfRangeFails() {
        assertThrows(ConfigurationException.class,
                () -> resolve(Map.of("APP_PORT", "99999999999"), () -> envInt("APP_PORT", 8080)));
    }

    @Test
    void invalidBooleanFails() {
        for (String value : List.of("yes", "1", "on", "tru")) {
            ConfigurationException failure = assertThrows(ConfigurationException.class,
                    () -> resolve(Map.of("APP_DEBUG", value), () -> envBool("APP_DEBUG", false)));
            assertTrue(failure.getMessage().contains("Invalid boolean value for environment variable:\nAPP_DEBUG"),
                    failure.getMessage());
        }
    }

    @Test
    void booleanIsCaseInsensitive() {
        assertEquals(true, resolve(Map.of("APP_DEBUG", "TRUE"), () -> envBool("APP_DEBUG", false)));
        assertEquals(false, resolve(Map.of("APP_DEBUG", "False"), () -> envBool("APP_DEBUG", true)));
    }

    @Test
    void invalidSecretValueIsMaskedInTheError() {
        ConfigurationException failure = assertThrows(ConfigurationException.class,
                () -> resolve(Map.of("API_KEY", "sk-live-123"), () -> envInt("API_KEY", 0)));

        assertTrue(failure.getMessage().contains("value = ********"), failure.getMessage());
        assertFalse(failure.getMessage().contains("sk-live-123"), failure.getMessage());
    }

    @Test
    void productionAllowsNonSecretDefaults() {
        assertEquals(8080, resolve(Map.of("APP_ENV", "production"), () -> envInt("APP_PORT", 8080)));
        assertEquals("Hello", resolve(Map.of("APP_ENV", "production"), () -> env("APP_NAME", "Hello")));
    }

    @Test
    void productionSecretFromTheProcessEnvironmentSucceeds() {
        assertEquals("from-process", resolve(Map.of("APP_ENV", "production", "JWT_SECRET", "from-process"),
                () -> env("JWT_SECRET", "default-secret")));
    }

    @Test
    void productionSecretCannotFallBackToADefault() {
        ConfigurationException failure = assertThrows(ConfigurationException.class,
                () -> resolve(Map.of("APP_ENV", "production"), () -> env("JWT_SECRET", "default-secret")));

        assertTrue(failure.getMessage().contains("JWT_SECRET"), failure.getMessage());
        assertFalse(failure.getMessage().contains("default-secret"), failure.getMessage());
    }

    @Test
    void productionSecretFromDotEnvDoesNotSatisfyTheSetting() throws IOException {
        writeDotEnv("DB_PASSWORD=from-dot-env", "API_KEY=from-dot-env");
        EnvResolver resolver = new EnvResolver(EnvironmentLoader.load(Map.of("APP_ENV", "production"), directory));

        ConfigurationException required = assertThrows(ConfigurationException.class,
                () -> resolver.within(() -> env("DB_PASSWORD")));
        ConfigurationException defaulted = assertThrows(ConfigurationException.class,
                () -> resolver.within(() -> env("API_KEY", "fallback")));

        assertFalse(required.getMessage().contains("from-dot-env"));
        assertFalse(defaulted.getMessage().contains("from-dot-env"));
        assertFalse(defaulted.getMessage().contains("fallback"));
    }

    @Test
    void developmentSecretMayUseADefault() {
        assertEquals("local-secret", resolve(Map.of(), () -> env("JWT_SECRET", "local-secret")));
    }

    @Test
    void resolvedValuesAreRecordedWithTheirSource() throws IOException {
        writeDotEnv("APP_NAME=Shop");
        EnvResolver resolver = new EnvResolver(EnvironmentLoader.load(Map.of("APP_PORT", "9000"), directory));

        resolver.within(() -> {
            env("APP_NAME");
            envInt("APP_PORT", 8080);
            envBool("APP_DEBUG", false);
            return null;
        });

        assertEquals(Map.of(
                        "APP_NAME", new ResolvedValue("Shop", ValueSource.DOT_ENV),
                        "APP_PORT", new ResolvedValue("9000", ValueSource.PROCESS),
                        "APP_DEBUG", new ResolvedValue("false", ValueSource.DEFAULT)),
                resolver.resolved());
    }

    @Test
    void envOutsideConfigurationIsRejected() {
        assertThrows(IllegalStateException.class, () -> env("APP_NAME"));
    }

    @Test
    void bindingIsRemovedAfterConfiguration() {
        EnvResolver resolver = resolver(Map.of("APP_NAME", "Shop"));
        resolver.within(() -> env("APP_NAME"));

        assertThrows(IllegalStateException.class, () -> env("APP_NAME"));
    }

    @Test
    void bindingIsRemovedWhenConfigurationFails() {
        EnvResolver resolver = resolver(Map.of());
        assertThrows(ConfigurationException.class, () -> resolver.within(() -> env("APP_NAME")));

        assertThrows(IllegalStateException.class, () -> env("APP_NAME"));
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> resolve(Map.of(), () -> env(null)));
        assertThrows(NullPointerException.class, () -> resolve(Map.of(), () -> env("APP_NAME", null)));
    }

    private <T> T resolve(Map<String, String> process, Supplier<T> action) {
        return resolver(process).within(action);
    }

    private EnvResolver resolver(Map<String, String> process) {
        return new EnvResolver(EnvironmentLoader.load(process, directory));
    }

    private void writeDotEnv(String... lines) throws IOException {
        Files.write(directory.resolve(".env"), List.of(lines), StandardCharsets.UTF_8);
    }
}
