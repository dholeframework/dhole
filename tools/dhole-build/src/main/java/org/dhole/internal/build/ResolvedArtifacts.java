package org.dhole.internal.build;

import java.nio.file.Path;
import java.util.List;

/**
 * The verified artifact files of an application build, in lock order.
 *
 * @param runtime the application's runtime class path (also its compile class path)
 * @param test the additional test tools
 * @param lockCreated whether this resolution wrote {@code dhole.lock}
 */
public record ResolvedArtifacts(List<Path> runtime, List<Path> test, boolean lockCreated) {

    public ResolvedArtifacts {
        runtime = List.copyOf(runtime);
        test = List.copyOf(test);
    }
}
