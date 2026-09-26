package org.dhole.internal.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectManifestTest {

    static final String MANIFEST = """
            # Project manifest
            [project]
            name = "hello"
            version = "0.1.0"
            java = "21"

            [dhole]
            version = "0.1.0"

            [dependencies]
            web = "0.1.0"   # the web stack

            [build]
            main = "hello.App"
            """;

    @Test
    void readsTheManifest() {
        assertEquals(new ProjectManifest("hello", "0.1.0", 21, "0.1.0", Map.of("web", "0.1.0"), "hello.App"),
                ProjectManifest.parse(MANIFEST));
    }

    @Test
    void emptyDependencyTableAndEscapesAreAccepted() {
        ProjectManifest manifest = ProjectManifest.parse(MANIFEST.replace("web = \"0.1.0\"   # the web stack", "")
                .replace("name = \"hello\"", "name = \"hello\" # \"quoted\" \\\\ comment"));

        assertEquals(Map.of(), manifest.dependencies());
    }

    @Test
    void invalidManifestsAreRejectedWithActionableMessages() {
        Map<String, String> cases = Map.ofEntries(
                Map.entry(MANIFEST.replace("name = \"hello\"", "name = \"Hello World\""), "project name 'Hello World'"),
                Map.entry(MANIFEST.replace("java = \"21\"", "java = \"17\""), "java = \"17\" is not supported"),
                Map.entry(MANIFEST.replace("main = \"hello.App\"", "main = \"App\""), "must be a fully qualified class name"),
                Map.entry(MANIFEST.replace("[build]\nmain = \"hello.App\"\n", ""), "missing [build] main"),
                Map.entry(MANIFEST + "\n[external-dependencies]\njsoup = \"org.jsoup:jsoup:1.17.2\"\n",
                        "[external-dependencies] is not supported"),
                Map.entry(MANIFEST + "\n[repositories]\n", "[repositories] is not supported"),
                Map.entry(MANIFEST + "\n[plugins]\n", "unknown table [plugins]"),
                Map.entry(MANIFEST.replace("java = \"21\"", "java = \"21\"\ngroup = \"x\""), "unknown key 'group' in [project]"),
                Map.entry(MANIFEST.replace("web = \"0.1.0\"", "web = \"latest version\""), "invalid version"));
        cases.forEach((text, expected) -> {
            BuildException failure = assertThrows(BuildException.class, () -> ProjectManifest.parse(text), expected);
            assertTrue(failure.getMessage().startsWith("Manifest Error\n\ndhole.toml: "), failure.getMessage());
            assertTrue(failure.getMessage().contains(expected), failure.getMessage());
        });
    }

    @Test
    void syntaxOutsideTheSupportedSubsetReportsTheLine() {
        for (String text : List.of("[project\n", "[[array]]\n", "name = \"x\"\n", "[a]\nkey = 21\n", "[a]\nkey = 'x'\n",
                "[a]\nkey\n", "[a]\nk = \"unterminated\n", "[a]\n[a]\n", "[a]\nk = \"x\"\nk = \"y\"\n", "[a]\nk = \"\\q\"\n")) {
            BuildException failure = assertThrows(BuildException.class, () -> ProjectManifest.parse(text), text);
            assertTrue(failure.getMessage().matches("(?s)Manifest Error\n\ndhole\\.toml:\\d+: .*"), failure.getMessage());
        }
    }

    @Test
    void missingManifestExplainsWhatToDo(@TempDir Path directory) {
        BuildException failure = assertThrows(BuildException.class, () -> ProjectManifest.read(directory));

        assertTrue(failure.getMessage().contains("dhole new <name>"), failure.getMessage());
    }
}
