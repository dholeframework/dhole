package org.dhole.internal.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Verifies the index written by the repository build: the {@code fixture} source set is compiled
 * by Gradle's JavaCompile with this module's jar on the annotation processor path and
 * {@code -Adhole.application=com.acme.fixture.App} (see build.gradle.kts). Nothing here runs the
 * processor.
 */
class FixtureBuildTest {

    @Test
    void repositoryBuildGeneratesTheFixtureIndex() throws IOException {
        String classes = System.getProperty("dhole.fixture.classes");
        assertNotNull(classes, "dhole.fixture.classes is set by the Gradle test task");
        Path index = Path.of(classes).resolve(ComponentIndexWriter.LOCATION);
        assertTrue(Files.isRegularFile(index), "the processor did not write " + ComponentIndexWriter.LOCATION);

        String content = Files.readString(index, StandardCharsets.UTF_8);

        assertTrue(content.startsWith("dhole-metadata 1\n"), content);
        assertEquals(String.join("\n",
                "dhole-metadata 1",
                "",
                "component com.acme.fixture.App",
                "unusable no-public-constructor",
                "source com/acme/fixture/App.java:6",
                "",
                "component com.acme.fixture.users.UserRepository",
                "constructor",
                "source com/acme/fixture/users/UserRepository.java:3",
                "",
                "component com.acme.fixture.users.UserService",
                "constructor com.acme.fixture.users.UserRepository",
                "source com/acme/fixture/users/UserService.java:7",
                ""), content);
        assertFalse(content.contains("com.acme.outside"), content);
    }
}
