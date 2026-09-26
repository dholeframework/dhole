package org.dhole.internal.build;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads the subset of TOML used by {@code dhole.toml}: {@code [table]} headers, bare keys with
 * basic-string values ({@code key = "value"}), comments and blank lines. Anything else is rejected
 * with its line number rather than guessed.
 */
final class Toml {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]+");

    private Toml() {
    }

    /**
     * @return tables in file order, each with its keys in file order
     * @throws BuildException if the text is outside the supported subset
     */
    static Map<String, Map<String, String>> parse(String text, String file) {
        Map<String, Map<String, String>> tables = new LinkedHashMap<>();
        Map<String, String> current = null;
        List<String> lines = text.lines().toList();
        for (int index = 0; index < lines.size(); index++) {
            int number = index + 1;
            String line = stripComment(lines.get(index), file, number).strip();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("[")) {
                if (!line.endsWith("]") || line.startsWith("[[")) {
                    throw error(file, number, "invalid table header");
                }
                String name = line.substring(1, line.length() - 1).strip();
                if (!NAME.matcher(name).matches()) {
                    throw error(file, number, "invalid table name '" + name + "'");
                }
                if (tables.containsKey(name)) {
                    throw error(file, number, "table [" + name + "] is declared more than once");
                }
                current = new LinkedHashMap<>();
                tables.put(name, current);
                continue;
            }
            int equals = line.indexOf('=');
            if (equals < 0) {
                throw error(file, number, "expected 'key = \"value\"'");
            }
            String key = line.substring(0, equals).strip();
            if (!NAME.matcher(key).matches()) {
                throw error(file, number, "invalid key '" + key + "'");
            }
            if (current == null) {
                throw error(file, number, "key '" + key + "' is outside a table");
            }
            if (current.containsKey(key)) {
                throw error(file, number, "key '" + key + "' is declared more than once");
            }
            current.put(key, string(line.substring(equals + 1).strip(), file, number));
        }
        tables.replaceAll((name, keys) -> Collections.unmodifiableMap(keys));
        return Collections.unmodifiableMap(tables);
    }

    private static String string(String value, String file, int line) {
        if (value.length() < 2 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
            throw error(file, line, "values must be double-quoted strings");
        }
        StringBuilder text = new StringBuilder();
        for (int index = 1; index < value.length() - 1; index++) {
            char character = value.charAt(index);
            if (character == '"') {
                throw error(file, line, "unexpected quote in string");
            }
            if (character == '\\') {
                if (index + 1 >= value.length() - 1) {
                    throw error(file, line, "unfinished escape");
                }
                char escaped = value.charAt(++index);
                switch (escaped) {
                    case '"', '\\' -> text.append(escaped);
                    case 'n' -> text.append('\n');
                    case 't' -> text.append('\t');
                    default -> throw error(file, line, "unsupported escape '\\" + escaped + "'");
                }
                continue;
            }
            text.append(character);
        }
        return text.toString();
    }

    private static String stripComment(String line, String file, int number) {
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '\\' && quoted) {
                index++;
            } else if (character == '"') {
                quoted = !quoted;
            } else if (character == '#' && !quoted) {
                return line.substring(0, index);
            }
        }
        if (quoted) {
            throw error(file, number, "unterminated string");
        }
        return line;
    }

    private static BuildException error(String file, int line, String problem) {
        return new BuildException("Manifest Error\n\n" + file + ":" + line + ": " + problem + ".");
    }
}
