package org.dhole.internal.config;

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
import java.util.Optional;

import org.dhole.config.ConfigurationException;
import org.dhole.config.Environment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EnvironmentLoaderTest {

    @TempDir
    Path directory;

    @Test
    void defaultsToDevelopmentWithoutAppEnvOrDotEnv() {
        Environment environment = EnvironmentLoader.load(Map.of(), directory);

        assertEquals("development", environment.name());
        assertTrue(environment.isDevelopment());
    }

    @ParameterizedTest
    @ValueSource(strings = {"development", "test", "production"})
    void acceptsEachOfficialEnvironmentFromTheProcess(String name) {
        Environment environment = EnvironmentLoader.load(Map.of("APP_ENV", name), directory);

        assertEquals(name, environment.name());
        assertEquals("development".equals(name), environment.isDevelopment());
        assertEquals("test".equals(name), environment.isTest());
        assertEquals("production".equals(name), environment.isProduction());
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "live", "local", "staging", "Production"})
    void rejectsUnknownEnvironmentNames(String name) {
        ConfigurationException failure = assertThrows(ConfigurationException.class,
                () -> EnvironmentLoader.load(Map.of("APP_ENV", name), directory));

        assertTrue(failure.getMessage().contains("APP_ENV"), failure.getMessage());
    }

    @Test
    void rejectsUnknownEnvironmentNameFromDotEnv() throws IOException {
        writeDotEnv("APP_ENV=staging");

        assertThrows(ConfigurationException.class, () -> EnvironmentLoader.load(Map.of(), directory));
    }

    @Test
    void loadsDotEnvInDevelopment() throws IOException {
        writeDotEnv("APP_NAME=Shop", "DB_PASSWORD=local");

        Environment environment = EnvironmentLoader.load(Map.of(), directory);

        assertEquals(Optional.of("Shop"), environment.get("APP_NAME"));
        assertEquals(Optional.of("local"), environment.get("DB_PASSWORD"));
    }

    @Test
    void loadsDotEnvInTest() throws IOException {
        writeDotEnv("APP_NAME=Shop");

        Environment environment = EnvironmentLoader.load(Map.of("APP_ENV", "test"), directory);

        assertEquals(Optional.of("Shop"), environment.get("APP_NAME"));
    }

    @Test
    void processEnvironmentOverridesDotEnv() throws IOException {
        writeDotEnv("APP_NAME=FromDotEnv", "APP_PORT=9000");

        Environment environment = EnvironmentLoader.load(Map.of("APP_NAME", "FromProcess"), directory);

        assertEquals(Optional.of("FromProcess"), environment.get("APP_NAME"));
        assertEquals(Optional.of("9000"), environment.get("APP_PORT"));
    }

    @Test
    void emptyProcessVariableIsNotReplacedByDotEnv() throws IOException {
        writeDotEnv("APP_NAME=FromDotEnv");

        Environment environment = EnvironmentLoader.load(Map.of("APP_NAME", ""), directory);

        assertEquals(Optional.empty(), environment.get("APP_NAME"));
    }

    @Test
    void emptyDotEnvValueIsAbsent() throws IOException {
        writeDotEnv("DB_USER=");

        assertEquals(Optional.empty(), EnvironmentLoader.load(Map.of(), directory).get("DB_USER"));
    }

    @Test
    void dotEnvIsIgnoredEntirelyInProduction() throws IOException {
        writeDotEnv("APP_NAME=FromDotEnv", "this line is malformed");

        Environment environment = EnvironmentLoader.load(Map.of("APP_ENV", "production"), directory);

        assertTrue(environment.isProduction());
        assertEquals(Optional.empty(), environment.get("APP_NAME"));
    }

    @Test
    void productionCannotBeSelectedByDotEnv() throws IOException {
        writeDotEnv("APP_ENV=production");

        ConfigurationException failure = assertThrows(ConfigurationException.class,
                () -> EnvironmentLoader.load(Map.of(), directory));

        assertTrue(failure.getMessage().contains("process environment"), failure.getMessage());
    }

    @Test
    void dotEnvCanSelectTheTestEnvironment() throws IOException {
        writeDotEnv("APP_ENV=test");

        assertTrue(EnvironmentLoader.load(Map.of(), directory).isTest());
    }

    @Test
    void malformedDotEnvFailsOutsideProduction() throws IOException {
        writeDotEnv("this line is malformed");

        assertThrows(ConfigurationException.class, () -> EnvironmentLoader.load(Map.of(), directory));
    }

    @Test
    void loadReadsTheRealProcessEnvironment() {
        Environment environment = EnvironmentLoader.load(Map.of("PATH_PROBE", "x"), directory);
        assertEquals(Optional.of("x"), environment.get("PATH_PROBE"));

        Map<String, String> real = System.getenv();
        Environment system = EnvironmentLoader.load();
        real.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .findFirst()
                .ifPresent(entry -> assertEquals(Optional.of(entry.getValue()), system.get(entry.getKey())));
    }

    @Test
    void textualRepresentationContainsNoValues() throws IOException {
        writeDotEnv("JWT_SECRET=top-secret-value");

        Environment environment = EnvironmentLoader.load(Map.of("DB_PASSWORD", "hunter2"), directory);

        assertFalse(environment.toString().contains("top-secret-value"));
        assertFalse(environment.toString().contains("hunter2"));
    }

    private void writeDotEnv(String... lines) throws IOException {
        Files.write(directory.resolve(".env"), List.of(lines), StandardCharsets.UTF_8);
    }
}
