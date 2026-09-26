package org.dhole.internal.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SecretNamesTest {

    @ParameterizedTest
    @ValueSource(strings = {"JWT_SECRET", "DB_PASSWORD", "API_KEY", "SECRET", "PASSWORD", "api_key"})
    void documentedSecretNamesAreSecretAndMasked(String name) {
        assertTrue(SecretNames.isSecret(name));
        assertEquals("********", SecretNames.display(name, "value"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"APP_NAME", "APP_PORT", "APP_ENV", "DATABASE_URL", "KEYBOARD", "SECRETARY_NAME"})
    void otherNamesAreShownAsIs(String name) {
        assertFalse(SecretNames.isSecret(name));
        assertEquals("value", SecretNames.display(name, "value"));
    }
}
