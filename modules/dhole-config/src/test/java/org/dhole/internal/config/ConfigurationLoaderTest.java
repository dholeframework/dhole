package org.dhole.internal.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.dhole.config.AppSettings;
import org.dhole.config.ConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigurationLoaderTest {

    @TempDir
    Path directory;

    // Settings lookup

    @Test
    void conventionalSettingsIsFoundAndConfigureIsInvoked() {
        LoadedConfiguration configuration = load(org.dhole.testapps.roadmap.App.class,
                Map.of("APP_NAME", "Shop", "APP_PORT", "9000"));

        assertEquals(new AppSettings("Shop", 9000), configuration.settings().app());
    }

    @Test
    void settingsClassNameIsDerivedFromTheApplicationPackage() {
        assertEquals("org.dhole.testapps.roadmap.config.Settings",
                SettingsLookup.settingsClassName(org.dhole.testapps.roadmap.App.class));
    }

    @Test
    void absentSettingsIsAllowedAndUnrelatedSettingsClassesAreNotUsed() {
        // org.dhole.testapps.absent has Settings classes in its own package, in a sub-package's
        // config package, and in the parent's config package; none of them is the convention.
        LoadedConfiguration configuration = load(org.dhole.testapps.absent.App.class, Map.of());

        assertEquals(new AppSettings("App", 8080), configuration.settings().app());
    }

    @Test
    void wrongParametersFailClearly() {
        assertInvalidSettings(org.dhole.testapps.wrongparameters.App.class, "the method has the wrong parameters");
    }

    @Test
    void missingConfigureMethodFailsClearly() {
        assertInvalidSettings(org.dhole.testapps.missingmethod.App.class, "the method is missing");
    }

    @Test
    void nonStaticConfigureFailsClearly() {
        assertInvalidSettings(org.dhole.testapps.nonstatic.App.class, "the method is not static");
    }

    @Test
    void nonPublicConfigureFailsClearly() {
        assertInvalidSettings(org.dhole.testapps.nonpublicmethod.App.class, "the method is not public");
    }

    @Test
    void nonPublicSettingsClassFailsClearly() {
        assertInvalidSettings(org.dhole.testapps.nonpublicclass.App.class, "the class is not public");
    }

    @Test
    void nonVoidConfigureFailsClearly() {
        assertInvalidSettings(org.dhole.testapps.nonvoid.App.class, "the method does not return void");
    }

    @Test
    void exceptionFromConfigureIsWrappedWithItsCause() {
        ConfigurationException failure = assertThrows(ConfigurationException.class,
                () -> load(org.dhole.testapps.throwing.App.class, Map.of()));

        assertTrue(failure.getMessage().contains(
                "org.dhole.testapps.throwing.config.Settings.configure(SettingsBuilder) failed:\n"
                        + "java.lang.IllegalStateException"), failure.getMessage());
        assertFalse(failure.getMessage().contains("hunter2"), failure.getMessage());
        assertInstanceOf(IllegalStateException.class, failure.getCause());
    }

    // Typed values, requirements and validation

    @Test
    void defaultsDeclaredInSettingsApplyWhenTheEnvironmentIsEmpty() {
        assertEquals(new AppSettings("Hello", 8080),
                load(org.dhole.testapps.roadmap.App.class, Map.of()).settings().app());
    }

    @Test
    void dotEnvProvidesTypedConfigurationLocally() throws IOException {
        writeDotEnv("APP_NAME=FromDotEnv", "APP_PORT=9100");

        assertEquals(new AppSettings("FromDotEnv", 9100),
                load(org.dhole.testapps.roadmap.App.class, Map.of()).settings().app());
    }

    @Test
    void processEnvironmentOverridesDotEnvForSettings() throws IOException {
        writeDotEnv("APP_NAME=FromDotEnv", "APP_PORT=9100");

        assertEquals(new AppSettings("FromProcess", 9100),
                load(org.dhole.testapps.roadmap.App.class, Map.of("APP_NAME", "FromProcess")).settings().app());
    }

    @Test
    void missingRequiredValueFailsLoadingAndNamesTheSection() {
        ConfigurationException failure = assertThrows(ConfigurationException.class,
                () -> load(org.dhole.testapps.required.App.class, Map.of()));

        assertEquals("Configuration Error\n\nMissing required environment variable:\nAPP_NAME"
                + "\n\nRequired by:\nApp settings", failure.getMessage());
    }

    @Test
    void invalidIntegerFailsLoading() {
        ConfigurationException failure = assertThrows(ConfigurationException.class,
                () -> load(org.dhole.testapps.roadmap.App.class, Map.of("APP_PORT", "eighty")));

        assertTrue(failure.getMessage().contains("Invalid integer value for environment variable:\nAPP_PORT"),
                failure.getMessage());
    }

    @Test
    void invalidBooleanFailsLoading() {
        ConfigurationException failure = assertThrows(ConfigurationException.class,
                () -> load(org.dhole.testapps.required.App.class, Map.of("APP_NAME", "Shop", "APP_DEBUG", "yes")));

        assertTrue(failure.getMessage().contains("Invalid boolean value for environment variable:\nAPP_DEBUG"),
                failure.getMessage());
    }

    @Test
    void outOfRangePortFailsValidation() {
        ConfigurationException failure = assertThrows(ConfigurationException.class,
                () -> load(org.dhole.testapps.roadmap.App.class, Map.of("APP_PORT", "70000")));

        assertEquals("Configuration Error\n\napp.port must be between 0 and 65535\n\nport = 70000",
                failure.getMessage());
    }

    @Test
    void blankNameFailsValidationTogetherWithOtherProblems() {
        ConfigurationException failure = assertThrows(ConfigurationException.class,
                () -> load(org.dhole.testapps.roadmap.App.class, Map.of("APP_NAME", "   ", "APP_PORT", "-1")));

        assertTrue(failure.getMessage().contains("app.name must not be blank"), failure.getMessage());
        assertTrue(failure.getMessage().contains("app.port must be between 0 and 65535"), failure.getMessage());
    }

    @Test
    void settingsCanReactToTheEnvironment() {
        assertEquals("Local development",
                load(org.dhole.testapps.environment.App.class, Map.of()).settings().app().name());
        assertEquals("Live production",
                load(org.dhole.testapps.environment.App.class, Map.of("APP_ENV", "production"))
                        .settings().app().name());
    }

    // Secrets

    @Test
    void reportMasksSecretsAndShowsOtherValues() {
        LoadedConfiguration configuration = load(org.dhole.testapps.secrets.App.class, Map.of(
                "JWT_SECRET", "jwt-from-process", "DB_PASSWORD", "db-from-process", "APP_NAME", "Shop"));

        assertEquals(String.join("\n",
                        "API_KEY        ********",
                        "APP_NAME       Shop",
                        "DB_PASSWORD    ********",
                        "JWT_SECRET     ********"),
                configuration.report());
    }

    @Test
    void secretValuesNeverAppearInDiagnostics() {
        LoadedConfiguration configuration = load(org.dhole.testapps.secrets.App.class, Map.of(
                "JWT_SECRET", "jwt-from-process", "DB_PASSWORD", "db-from-process"));

        for (String text : List.of(configuration.report(), configuration.toString(),
                configuration.environment().toString(), configuration.values().toString())) {
            assertFalse(text.contains("jwt-from-process"), text);
            assertFalse(text.contains("db-from-process"), text);
            assertFalse(text.contains("local-api-key"), text);
        }
    }

    // Production restrictions

    @Test
    void productionUsesNonSecretDefaults() {
        assertEquals(new AppSettings("Hello", 8080),
                load(org.dhole.testapps.roadmap.App.class, Map.of("APP_ENV", "production")).settings().app());
    }

    @Test
    void productionIgnoresDotEnv() throws IOException {
        writeDotEnv("APP_NAME=FromDotEnv", "APP_PORT=9100");

        assertEquals(new AppSettings("Hello", 8080),
                load(org.dhole.testapps.roadmap.App.class, Map.of("APP_ENV", "production")).settings().app());
    }

    @Test
    void productionSecretsFromTheProcessEnvironmentSucceed() {
        LoadedConfiguration configuration = load(org.dhole.testapps.secrets.App.class, Map.of(
                "APP_ENV", "production", "JWT_SECRET", "a", "DB_PASSWORD", "b", "API_KEY", "c"));

        assertEquals("Secrets", configuration.settings().app().name());
    }

    @Test
    void productionSecretFallingBackToADefaultFailsWithoutExposingIt() {
        ConfigurationException failure = assertThrows(ConfigurationException.class,
                () -> load(org.dhole.testapps.secrets.App.class, Map.of("APP_ENV", "production")));

        assertTrue(failure.getMessage().contains("JWT_SECRET"), failure.getMessage());
        assertFalse(failure.getMessage().contains("local-jwt-secret"), failure.getMessage());
    }

    @Test
    void productionSecretsInDotEnvDoNotSatisfyTheSettings() throws IOException {
        writeDotEnv("JWT_SECRET=a", "DB_PASSWORD=b", "API_KEY=c");

        assertThrows(ConfigurationException.class,
                () -> load(org.dhole.testapps.secrets.App.class, Map.of("APP_ENV", "production")));
    }

    @Test
    void invalidAppEnvFailsLoading() {
        assertThrows(ConfigurationException.class,
                () -> load(org.dhole.testapps.roadmap.App.class, Map.of("APP_ENV", "prod")));
    }

    private LoadedConfiguration load(Class<?> applicationClass, Map<String, String> process) {
        return ConfigurationLoader.load(applicationClass, EnvironmentLoader.load(process, directory));
    }

    private void assertInvalidSettings(Class<?> applicationClass, String problem) {
        ConfigurationException failure = assertThrows(ConfigurationException.class,
                () -> load(applicationClass, Map.of()));

        assertTrue(failure.getMessage().contains(
                "must declare:\npublic static void configure(SettingsBuilder settings)"), failure.getMessage());
        assertTrue(failure.getMessage().endsWith("Problem:\n" + problem), failure.getMessage());
    }

    private void writeDotEnv(String... lines) throws IOException {
        Files.write(directory.resolve(".env"), List.of(lines), StandardCharsets.UTF_8);
    }
}
