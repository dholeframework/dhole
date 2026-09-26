package org.dhole.internal.build;

import java.nio.file.Path;

/**
 * The conventional directories of a Dhole project (PROJECT_STRUCTURE.md, BUILD_SYSTEM.md §11).
 */
public record ProjectLayout(Path root) {

    public ProjectLayout {
        root = root.toAbsolutePath().normalize();
    }

    public Path mainSources() {
        return root.resolve("src/main/java");
    }

    public Path mainResources() {
        return root.resolve("src/main/resources");
    }

    public Path testSources() {
        return root.resolve("src/test/java");
    }

    public Path testResources() {
        return root.resolve("src/test/resources");
    }

    public Path build() {
        return root.resolve("build");
    }

    public Path mainClasses() {
        return build().resolve("classes/main");
    }

    public Path testClasses() {
        return build().resolve("classes/test");
    }

    public Path distributions() {
        return build().resolve("distributions");
    }

    /**
     * Working state of tooling such as {@code dhole dev}; not build output.
     */
    public Path state() {
        return root.resolve(".dhole");
    }

    public String relative(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        return (absolute.startsWith(root) ? root.relativize(absolute) : absolute).toString().replace('\\', '/');
    }
}
