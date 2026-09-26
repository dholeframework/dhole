package org.dhole.internal.compiler;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Metadata of one concrete application class, as written to {@code components.idx}.
 *
 * @param type binary name of the class
 * @param constructor parameter types (in {@code Class.forName} format) of the single public
 *        constructor, when exactly one exists and the class passes the structural checks
 * @param unusable why the class cannot be a component ({@code reason [arguments]}), if it cannot
 * @param supertypes binary names of all transitive supertypes except {@code java.lang.Object}, sorted
 */
record ComponentRecord(
        String type,
        Optional<List<String>> constructor,
        Optional<String> unusable,
        List<String> supertypes,
        Optional<SourceLocation> source) {

    ComponentRecord {
        Objects.requireNonNull(type, "type");
        constructor = constructor.map(List::copyOf);
        Objects.requireNonNull(unusable, "unusable");
        supertypes = List.copyOf(supertypes);
        Objects.requireNonNull(source, "source");
    }
}
