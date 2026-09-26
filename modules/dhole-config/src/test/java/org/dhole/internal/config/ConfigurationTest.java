package org.dhole.internal.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.dhole.config.AppSettings;
import org.dhole.config.ConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigurationTest {

    @TempDir
    Path directory;

    @Test
    void loadsEnvironmentSettingsAndMaskedReport() throws IOException {
        Files.writeString(directory.resolve(".env"), "APP_PORT=9100\nAPP_ENV=test\n", StandardCharsets.UTF_8);

        Configuration configuration = Configuration.load(org.dhole.testapps.roadmap.App.class,
                Map.of("APP_NAME", "Shop", "DB_PASSWORD", "hunter2"), directory);

        assertEquals("test", configuration.environment().name());
        assertEquals(new AppSettings("Shop", 9100), configuration.app());
        assertEquals(configuration.app(), configuration.settings().app());
        assertEquals("APP_NAME    Shop\nAPP_PORT    9100", configuration.report());
    }

    @Test
    void invalidConfigurationFails() {
        assertThrows(ConfigurationException.class, () -> Configuration.load(org.dhole.testapps.roadmap.App.class,
                Map.of("APP_PORT", "not-a-port"), directory));
    }
}
