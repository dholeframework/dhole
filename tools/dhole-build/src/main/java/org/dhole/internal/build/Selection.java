package org.dhole.internal.build;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The artifacts an application build selects from the installed distribution: the runtime closure
 * of {@code dhole-core} plus every declared official module, and the test tools not already in the
 * runtime closure. Deterministic: sorted by coordinates.
 */
public record Selection(List<Coordinates> runtime, List<Coordinates> test) {

    static final String GROUP = "org.dhole";

    public Selection {
        runtime = List.copyOf(runtime);
        test = List.copyOf(test);
    }

    /**
     * @throws BuildException if a dependency is unknown or its version is not the installed one
     */
    public static Selection of(ProjectManifest manifest, Distribution distribution) {
        String installed = distribution.version();
        if (!manifest.dholeVersion().equals(installed)) {
            throw new BuildException("Dependency Error\n\ndhole.toml requires Dhole " + manifest.dholeVersion()
                    + " but the installed Dhole is " + installed + ".\n\nInstall Dhole " + manifest.dholeVersion()
                    + ", or set [dhole] version = \"" + installed + "\" to use the installed version.");
        }
        List<Coordinates> roots = new ArrayList<>();
        roots.add(new Coordinates(GROUP, "dhole-core", installed));
        manifest.dependencies().forEach((module, version) -> {
            Coordinates coordinates = new Coordinates(GROUP, "dhole-" + module, installed);
            if (module.equals("core") || distribution.artifact(coordinates).isEmpty()) {
                throw new BuildException("Dependency Error\n\nUnknown Dhole module '" + module + "' in [dependencies]."
                        + "\n\nAvailable modules: " + String.join(", ", modules(distribution)) + ".");
            }
            if (!version.equals(installed)) {
                throw new BuildException("Dependency Error\n\n" + module + " = \"" + version + "\" but the installed Dhole is "
                        + installed + ".\n\nOfficial modules use the Dhole version: set " + module + " = \"" + installed + "\".");
            }
            roots.add(coordinates);
        });
        Set<Coordinates> runtime = closure(roots, distribution);
        Set<Coordinates> test = closure(distribution.testRoots(), distribution);
        test.removeAll(runtime);
        return new Selection(List.copyOf(runtime), List.copyOf(test));
    }

    private static Set<Coordinates> closure(List<Coordinates> roots, Distribution distribution) {
        Set<Coordinates> selected = new TreeSet<>();
        List<Coordinates> pending = new ArrayList<>(roots);
        while (!pending.isEmpty()) {
            Coordinates next = pending.remove(pending.size() - 1);
            if (selected.add(next)) {
                Distribution.Artifact artifact = distribution.artifact(next).orElseThrow(() -> new BuildException(
                        "Installation Error\n\nThe installed Dhole distribution does not contain " + next
                                + ".\n\nReinstall Dhole."));
                pending.addAll(artifact.requires());
            }
        }
        return selected;
    }

    private static List<String> modules(Distribution distribution) {
        return distribution.artifacts().stream().map(Distribution.Artifact::coordinates)
                .filter(coordinates -> coordinates.group().equals(GROUP) && coordinates.name().startsWith("dhole-")
                        && !coordinates.name().equals("dhole-core"))
                .map(coordinates -> coordinates.name().substring("dhole-".length()))
                .sorted().toList();
    }
}
