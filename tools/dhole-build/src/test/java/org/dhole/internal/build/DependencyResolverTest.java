package org.dhole.internal.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DependencyResolverTest {

    @TempDir
    Path home;

    @TempDir
    Path project;

    private Distribution distribution;
    private final ProjectManifest manifest = ProjectManifest.parse(ProjectManifestTest.MANIFEST);

    @BeforeEach
    void install() throws IOException {
        distribution = FakeDistribution.create(home);
    }

    @Test
    void firstBuildLocksTheSelectionWithChecksums() throws IOException {
        ResolvedArtifacts artifacts = DependencyResolver.resolve(project, manifest, distribution, false);

        assertTrue(artifacts.lockCreated());
        assertEquals(List.of("json-lib-2.0.jar", "dhole-config-0.1.0.jar", "dhole-core-0.1.0.jar", "dhole-http-0.1.0.jar",
                "dhole-routing-0.1.0.jar", "dhole-web-0.1.0.jar"), names(artifacts.runtime()));
        assertEquals(List.of("junit-jupiter-6.1.3.jar", "opentest4j-1.3.0.jar"), names(artifacts.test()));
        String lock = Files.readString(project.resolve(LockFile.FILE));
        assertTrue(lock.startsWith("dhole-lock 1\ndhole 0.1.0\n\nartifact com.example:json-lib:2.0\nscope runtime\nsha256 "
                + LockFile.sha256(home.resolve("lib/json-lib-2.0.jar")) + "\n\nartifact org.dhole:dhole-config:0.1.0\n"), lock);
        assertTrue(lock.contains("\nartifact org.dhole:dhole-web:0.1.0\nscope runtime\n"), lock);
        assertTrue(lock.endsWith("artifact org.opentest4j:opentest4j:1.3.0\nscope test\nsha256 "
                + LockFile.sha256(home.resolve("lib/opentest4j-1.3.0.jar")) + "\n"), lock);
        assertFalse(lock.contains("/") || lock.contains("\\") || lock.contains(home.toString()), "no machine paths");
    }

    @Test
    void sameSelectionProducesAByteForByteIdenticalLock(@TempDir Path other) throws IOException {
        DependencyResolver.resolve(project, manifest, distribution, false);
        DependencyResolver.resolve(other, manifest, distribution, false);

        assertEquals(-1L, Files.mismatch(project.resolve(LockFile.FILE), other.resolve(LockFile.FILE)));
    }

    @Test
    void laterBuildsVerifyAndNeverRewriteTheLock() throws IOException {
        DependencyResolver.resolve(project, manifest, distribution, false);
        Path lock = project.resolve(LockFile.FILE);
        String before = Files.readString(lock);

        ResolvedArtifacts again = DependencyResolver.resolve(project, manifest, distribution, false);

        assertFalse(again.lockCreated());
        assertEquals(before, Files.readString(lock));
        assertEquals(8, DependencyResolver.verify(project, manifest, distribution));
    }

    @Test
    void changedArtifactBytesFailTheBuild() throws IOException {
        DependencyResolver.resolve(project, manifest, distribution, false);
        Files.writeString(home.resolve("lib/dhole-routing-0.1.0.jar"), "tampered");
        String lock = Files.readString(project.resolve(LockFile.FILE));

        BuildException failure = assertThrows(BuildException.class,
                () -> DependencyResolver.resolve(project, manifest, distribution, false));

        assertTrue(failure.getMessage().startsWith("Checksum Error\n\nThe installed artifact org.dhole:dhole-routing:0.1.0 "
                + "does not match dhole.lock."), failure.getMessage());
        assertEquals(lock, Files.readString(project.resolve(LockFile.FILE)), "the lock is not rewritten");
    }

    @Test
    void missingArtifactFailsTheBuild() throws IOException {
        DependencyResolver.resolve(project, manifest, distribution, false);
        Files.delete(home.resolve("lib/opentest4j-1.3.0.jar"));

        BuildException failure = assertThrows(BuildException.class,
                () -> DependencyResolver.resolve(project, manifest, distribution, false));

        assertTrue(failure.getMessage().contains("org.opentest4j:opentest4j:1.3.0 is missing"), failure.getMessage());
    }

    @Test
    void manifestChangesRequireAnExplicitLockUpdate() throws IOException {
        ProjectManifest coreOnly = ProjectManifest.parse(ProjectManifestTest.MANIFEST.replace("web = \"0.1.0\"", ""));
        DependencyResolver.resolve(project, coreOnly, distribution, false);

        BuildException failure = assertThrows(BuildException.class,
                () -> DependencyResolver.resolve(project, manifest, distribution, false));

        assertTrue(failure.getMessage().contains("  + org.dhole:dhole-web:0.1.0\n"), failure.getMessage());
        assertTrue(failure.getMessage().contains("dhole build --update-lock"), failure.getMessage());
        DependencyResolver.resolve(project, manifest, distribution, true);
        assertTrue(Files.readString(project.resolve(LockFile.FILE)).contains("org.dhole:dhole-web:0.1.0"));
    }

    @Test
    void lockFromAnotherDholeVersionIsNotSilentlySubstituted() throws IOException {
        DependencyResolver.resolve(project, manifest, distribution, false);
        Path lock = project.resolve(LockFile.FILE);
        Files.writeString(lock, Files.readString(lock).replace("dhole 0.1.0\n", "dhole 0.2.0\n"));

        BuildException failure = assertThrows(BuildException.class,
                () -> DependencyResolver.resolve(project, manifest, distribution, false));

        assertTrue(failure.getMessage().contains("was written for Dhole 0.2.0 but the installed Dhole is 0.1.0"),
                failure.getMessage());
    }

    @Test
    void unsupportedAndMalformedLocksAreClearErrors() throws IOException {
        Path lock = project.resolve(LockFile.FILE);
        Files.writeString(lock, "dhole-lock 2\ndhole 0.1.0\n");
        assertTrue(assertThrows(BuildException.class, () -> DependencyResolver.resolve(project, manifest, distribution, false))
                .getMessage().contains("has format 'dhole-lock 2'; this Dhole version reads 'dhole-lock 1'"));

        for (String text : List.of("lock\n", "dhole-lock 1\n", "dhole-lock 1\ndhole 0.1.0\nartifact a:b:1\n",
                "dhole-lock 1\ndhole 0.1.0\n\nartifact a:b:1\nscope compile\nsha256 " + "0".repeat(64) + "\n",
                "dhole-lock 1\ndhole 0.1.0\n\nartifact a:b:1\nscope runtime\nsha256 xyz\n",
                "dhole-lock 1\ndhole 0.1.0\n\nartifact a:b\nscope runtime\nsha256 " + "0".repeat(64) + "\n")) {
            Files.writeString(lock, text);
            BuildException failure = assertThrows(BuildException.class,
                    () -> DependencyResolver.resolve(project, manifest, distribution, false), text);
            assertTrue(failure.getMessage().startsWith("Lock Error\n\nMalformed dhole.lock at line "), failure.getMessage());
        }
    }

    @Test
    void lockIsReusableOnAnotherMachineWithTheSameDistribution(@TempDir Path machine) throws IOException {
        DependencyResolver.resolve(project, manifest, distribution, false);
        Path otherHome = Files.createDirectories(machine.resolve("dhole home with spaces"));
        Distribution copy = FakeDistribution.create(otherHome);
        Path otherProject = Files.createDirectories(machine.resolve("checkout"));
        Files.copy(project.resolve(LockFile.FILE), otherProject.resolve(LockFile.FILE));

        ResolvedArtifacts artifacts = DependencyResolver.resolve(otherProject, manifest, copy, false);

        assertFalse(artifacts.lockCreated());
        assertTrue(artifacts.runtime().stream().allMatch(path -> path.startsWith(otherHome)));
    }

    @Test
    void versionsAndModulesAreChecked() {
        assertTrue(assertThrows(BuildException.class, () -> Selection.of(ProjectManifest.parse(ProjectManifestTest.MANIFEST
                .replace("[dhole]\nversion = \"0.1.0\"", "[dhole]\nversion = \"0.9.0\"")), distribution))
                .getMessage().contains("dhole.toml requires Dhole 0.9.0 but the installed Dhole is 0.1.0"));
        assertTrue(assertThrows(BuildException.class, () -> Selection.of(ProjectManifest.parse(ProjectManifestTest.MANIFEST
                .replace("web = \"0.1.0\"", "web = \"0.2.0\"")), distribution))
                .getMessage().contains("web = \"0.2.0\" but the installed Dhole is 0.1.0"));
        assertTrue(assertThrows(BuildException.class, () -> Selection.of(ProjectManifest.parse(ProjectManifestTest.MANIFEST
                .replace("web = \"0.1.0\"", "graphql = \"0.1.0\"")), distribution))
                .getMessage().contains("Unknown Dhole module 'graphql' in [dependencies].\n\nAvailable modules: config, http, "
                        + "routing, web."));
    }

    @Test
    void generatedModulesIndexFollowsTheSelectedArtifacts() throws IOException {
        ResolvedArtifacts artifacts = DependencyResolver.resolve(project, manifest, distribution, false);

        assertEquals("""
                dhole-modules 1

                module config

                module http

                module routing
                requires http

                module web
                activator org.dhole.internal.web.WebActivator
                requires config http routing
                """, ModulesIndexGenerator.generate(artifacts.runtime()));
        assertEquals(ModulesIndexGenerator.generate(artifacts.runtime()),
                ModulesIndexGenerator.generate(artifacts.runtime().reversed()));
    }

    @Test
    void brokenModuleGraphFailsTheBuild() throws IOException {
        Path web = home.resolve("lib/dhole-web-0.1.0.jar");
        FakeDistribution.jar(web, "module web\nrequires security\n");

        BuildException failure = assertThrows(BuildException.class, () -> ModulesIndexGenerator.generate(List.of(web)));

        assertEquals("Module Dependency Error\n\nModule 'web' requires module 'security', which is not part of the application.",
                failure.getMessage());
    }

    private static List<String> names(List<Path> files) {
        try (Stream<Path> stream = files.stream()) {
            return stream.map(path -> path.getFileName().toString()).toList();
        }
    }
}
