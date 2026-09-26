package org.dhole.internal.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.dhole.config.ConfigurationException;
import org.junit.jupiter.api.Test;

class DotEnvParserTest {

    @Test
    void parsesEntriesAndIgnoresBlankLinesAndComments() {
        Map<String, String> values = DotEnvParser.parse(List.of(
                "# local settings",
                "APP_NAME=Shop",
                "",
                "   ",
                "  # indented comment",
                "APP_PORT=8080"), ".env");

        assertEquals(Map.of("APP_NAME", "Shop", "APP_PORT", "8080"), values);
    }

    @Test
    void trimsKeysAndUnquotedValues() {
        assertEquals(Map.of("APP_NAME", "My Shop"),
                DotEnvParser.parse(List.of("  APP_NAME  =   My Shop  "), ".env"));
    }

    @Test
    void quotedValuesAreLiteralWithoutQuotes() {
        Map<String, String> values = DotEnvParser.parse(List.of(
                "DOUBLE=\"  spaced # not a comment  \"",
                "SINGLE='a=b'",
                "EMPTY_QUOTED=\"\""), ".env");

        assertEquals(Map.of("DOUBLE", "  spaced # not a comment  ", "SINGLE", "a=b", "EMPTY_QUOTED", ""),
                values);
    }

    @Test
    void unquotedValueKeepsHashAndEqualsSigns() {
        assertEquals(Map.of("URL", "postgresql://h/db?a=b#frag"),
                DotEnvParser.parse(List.of("URL=postgresql://h/db?a=b#frag"), ".env"));
    }

    @Test
    void emptyValueIsKept() {
        assertEquals(Map.of("DB_USER", ""), DotEnvParser.parse(List.of("DB_USER="), ".env"));
    }

    @Test
    void lineWithoutSeparatorIsRejectedWithItsLineNumber() {
        ConfigurationException failure = assertThrows(ConfigurationException.class,
                () -> DotEnvParser.parse(List.of("APP_NAME=Shop", "JUST_A_WORD"), ".env"));

        assertTrue(failure.getMessage().contains("Malformed .env at line 2"), failure.getMessage());
    }

    @Test
    void invalidVariableNameIsRejected() {
        assertThrows(ConfigurationException.class,
                () -> DotEnvParser.parse(List.of("export APP_NAME=Shop"), ".env"));
        assertThrows(ConfigurationException.class,
                () -> DotEnvParser.parse(List.of("1APP=Shop"), ".env"));
        assertThrows(ConfigurationException.class,
                () -> DotEnvParser.parse(List.of("=Shop"), ".env"));
    }

    @Test
    void duplicateVariableIsRejected() {
        ConfigurationException failure = assertThrows(ConfigurationException.class,
                () -> DotEnvParser.parse(List.of("APP_NAME=A", "APP_NAME=B"), ".env"));

        assertTrue(failure.getMessage().contains("duplicate variable APP_NAME"), failure.getMessage());
    }

    @Test
    void unterminatedQuoteIsRejectedWithoutExposingTheValue() {
        ConfigurationException failure = assertThrows(ConfigurationException.class,
                () -> DotEnvParser.parse(List.of("JWT_SECRET=\"top-secret-value"), ".env"));

        assertTrue(failure.getMessage().contains("JWT_SECRET"), failure.getMessage());
        assertFalse(failure.getMessage().contains("top-secret-value"), failure.getMessage());
    }
}
