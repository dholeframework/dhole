package org.dhole.internal.build;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Resolves an application's artifacts from the installed distribution and reconciles them with
 * {@code dhole.lock} (BUILD_SYSTEM.md §21):
 *
 * <ul>
 *   <li>no lock: the selection is locked with the SHA-256 of each artifact and the lock is written;</li>
 *   <li>lock present: it must describe exactly the current selection and Dhole version, and every
 *       artifact's bytes must match its SHA-256; otherwise the build fails and the lock is untouched;</li>
 *   <li>update requested: the current selection is locked again (an explicit workflow).</li>
 * </ul>
 */
public final class DependencyResolver {

    private DependencyResolver() {
    }

    /**
     * @throws BuildException if the selection, the lock or an artifact is invalid
     */
    public static ResolvedArtifacts resolve(Path project, ProjectManifest manifest, Distribution distribution,
            boolean updateLock) {
        Selection selection = Selection.of(manifest, distribution);
        Optional<LockFile> existing = updateLock ? Optional.empty() : LockFile.read(project);
        if (existing.isEmpty()) {
            LockFile lock = lock(selection, distribution);
            lock.write(project);
            return files(lock, distribution, true);
        }
        LockFile lock = existing.get();
        if (!lock.dholeVersion().equals(distribution.version())) {
            throw new BuildException("Lock Error\n\n" + LockFile.FILE + " was written for Dhole " + lock.dholeVersion()
                    + " but the installed Dhole is " + distribution.version() + ".\n\nInstall Dhole " + lock.dholeVersion()
                    + ", or select the artifacts of the installed version with 'dhole build --update-lock'.");
        }
        Map<Coordinates, LockFile.Scope> expected = scopes(selection);
        Map<Coordinates, LockFile.Scope> locked = new TreeMap<>();
        lock.entries().forEach(entry -> locked.put(entry.coordinates(), entry.scope()));
        if (!expected.equals(locked)) {
            throw new BuildException("Lock Error\n\n" + LockFile.FILE + " does not match the dependencies of "
                    + ProjectManifest.FILE + ":\n" + difference(expected, locked)
                    + "\nReview the change and update the lock with 'dhole build --update-lock'.");
        }
        for (LockFile.Entry entry : lock.entries()) {
            String actual = LockFile.sha256(installed(entry.coordinates(), distribution));
            if (!actual.equals(entry.sha256())) {
                throw new BuildException("Checksum Error\n\nThe installed artifact " + entry.coordinates()
                        + " does not match " + LockFile.FILE + ".\n\nexpected sha256 " + entry.sha256() + "\nactual   sha256 "
                        + actual + "\n\nThe Dhole installation may be damaged or modified. Reinstall Dhole; do not edit "
                        + LockFile.FILE + " to hide the difference.");
            }
        }
        return files(lock, distribution, false);
    }

    /**
     * Verifies an existing lock without writing anything; for {@code dhole doctor}.
     *
     * @return the number of verified artifacts
     * @throws BuildException describing the first problem
     */
    public static int verify(Path project, ProjectManifest manifest, Distribution distribution) {
        if (!Files.exists(project.resolve(LockFile.FILE))) {
            throw new BuildException(LockFile.FILE + " does not exist yet; 'dhole build' creates it.");
        }
        ResolvedArtifacts artifacts = resolve(project, manifest, distribution, false);
        return artifacts.runtime().size() + artifacts.test().size();
    }

    private static LockFile lock(Selection selection, Distribution distribution) {
        List<LockFile.Entry> entries = new ArrayList<>();
        scopes(selection).forEach((coordinates, scope) ->
                entries.add(new LockFile.Entry(coordinates, scope, LockFile.sha256(installed(coordinates, distribution)))));
        return new LockFile(distribution.version(), entries);
    }

    private static Map<Coordinates, LockFile.Scope> scopes(Selection selection) {
        Map<Coordinates, LockFile.Scope> scopes = new TreeMap<>();
        selection.runtime().forEach(coordinates -> scopes.put(coordinates, LockFile.Scope.RUNTIME));
        selection.test().forEach(coordinates -> scopes.put(coordinates, LockFile.Scope.TEST));
        return scopes;
    }

    private static ResolvedArtifacts files(LockFile lock, Distribution distribution, boolean created) {
        List<Path> runtime = new ArrayList<>();
        List<Path> test = new ArrayList<>();
        for (LockFile.Entry entry : lock.entries()) {
            (entry.scope() == LockFile.Scope.RUNTIME ? runtime : test).add(installed(entry.coordinates(), distribution));
        }
        return new ResolvedArtifacts(runtime, test, created);
    }

    private static Path installed(Coordinates coordinates, Distribution distribution) {
        Distribution.Artifact artifact = distribution.artifact(coordinates).orElseThrow(() -> new BuildException(
                "Lock Error\n\nThe locked artifact " + coordinates + " is not part of the installed Dhole distribution."
                        + "\n\nInstall the Dhole version that wrote " + LockFile.FILE + "."));
        Path file = distribution.file(artifact);
        if (!Files.isRegularFile(file)) {
            throw new BuildException("Installation Error\n\nThe artifact " + coordinates + " is missing from the Dhole "
                    + "installation (lib/" + artifact.file() + ").\n\nReinstall Dhole.");
        }
        return file;
    }

    private static String difference(Map<Coordinates, LockFile.Scope> expected, Map<Coordinates, LockFile.Scope> locked) {
        Set<Coordinates> all = new TreeSet<>(expected.keySet());
        all.addAll(locked.keySet());
        StringBuilder text = new StringBuilder();
        for (Coordinates coordinates : all) {
            LockFile.Scope wanted = expected.get(coordinates);
            LockFile.Scope have = locked.get(coordinates);
            if (have == null) {
                text.append("  + ").append(coordinates).append('\n');
            } else if (wanted == null) {
                text.append("  - ").append(coordinates).append('\n');
            } else if (wanted != have) {
                text.append("  ~ ").append(coordinates).append(" (scope)\n");
            }
        }
        return text.toString();
    }
}
