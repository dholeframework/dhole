package org.dhole.internal.compiler;

import java.util.Comparator;
import java.util.List;

/**
 * Writes {@code META-INF/dhole/components.idx}, format version 1 (METADATA_COMPILER.md §34.2).
 * Output depends only on the records: blocks are sorted by type name and supertypes are sorted.
 */
final class ComponentIndexWriter {

    static final String LOCATION = "META-INF/dhole/components.idx";
    static final String HEADER = "dhole-metadata 1";

    private ComponentIndexWriter() {
    }

    static String write(List<ComponentRecord> records) {
        StringBuilder text = new StringBuilder(HEADER).append('\n');
        records.stream().sorted(Comparator.comparing(ComponentRecord::type)).forEach(record -> {
            text.append('\n').append("component ").append(record.type()).append('\n');
            record.constructor().ifPresent(parameters -> {
                text.append("constructor");
                parameters.forEach(parameter -> text.append(' ').append(parameter));
                text.append('\n');
            });
            record.unusable().ifPresent(reason -> text.append("unusable ").append(reason).append('\n'));
            record.supertypes().forEach(supertype -> text.append("supertype ").append(supertype).append('\n'));
            record.source().ifPresent(source -> text.append("source ").append(source).append('\n'));
        });
        return text.toString();
    }
}
