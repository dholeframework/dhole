package org.dhole.internal.module;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One module of {@code modules.idx}: its stable ID, optional activator class and required module IDs.
 */
public record ModuleDescriptor(String id, Optional<String> activator, List<String> requires) {

    public ModuleDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(activator, "activator");
        requires = List.copyOf(requires);
    }
}
