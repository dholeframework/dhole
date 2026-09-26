package org.dhole.internal.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.dhole.config.ConfigurationException;

/**
 * Parses {@code .env} content.
 *
 * <p>Supported syntax, one entry per line:
 * <ul>
 *   <li>blank lines and lines starting with {@code #} are ignored;</li>
 *   <li>{@code KEY=VALUE}, where {@code KEY} matches {@code [A-Za-z_][A-Za-z0-9_]*};</li>
 *   <li>whitespace around the key and the value is removed;</li>
 *   <li>a value wrapped in matching {@code "} or {@code '} quotes is taken literally without the
 *       quotes (no escapes, no interpolation); an unquoted value is taken literally, including
 *       any {@code #}.</li>
 * </ul>
 * A line without {@code =}, an invalid key, an unterminated quote or a duplicate key is rejected.
 * Error messages name the file, line and key, never the value.
 */
final class DotEnvParser {

    private static final Pattern KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private DotEnvParser() {
    }

    static Map<String, String> parse(List<String> lines, String source) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            int lineNumber = index + 1;
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int separator = trimmed.indexOf('=');
            if (separator < 0) {
                throw malformed(source, lineNumber, "expected KEY=VALUE");
            }
            String key = trimmed.substring(0, separator).strip();
            if (!KEY.matcher(key).matches()) {
                throw malformed(source, lineNumber, "invalid variable name");
            }
            String value = unquote(trimmed.substring(separator + 1).strip(), source, lineNumber, key);
            if (values.putIfAbsent(key, value) != null) {
                throw malformed(source, lineNumber, "duplicate variable " + key);
            }
        }
        return values;
    }

    private static String unquote(String value, String source, int lineNumber, String key) {
        if (value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        if (first != '"' && first != '\'') {
            return value;
        }
        if (value.length() < 2 || value.charAt(value.length() - 1) != first) {
            throw malformed(source, lineNumber, "unterminated quoted value for " + key);
        }
        return value.substring(1, value.length() - 1);
    }

    private static ConfigurationException malformed(String source, int lineNumber, String reason) {
        return new ConfigurationException(
                "Configuration Error\n\nMalformed " + source + " at line " + lineNumber + ":\n" + reason);
    }
}
