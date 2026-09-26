package org.dhole.internal.compiler;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Validation metadata of one type, as written to {@code validation.idx}: its fields in declaration
 * order.
 */
record ValidationRecord(String type, List<Field> fields, Optional<SourceLocation> source) {

    static final String LOCATION = "META-INF/dhole/validation.idx";

    ValidationRecord {
        Objects.requireNonNull(type, "type");
        fields = List.copyOf(fields);
        Objects.requireNonNull(source, "source");
    }

    record Field(String name, String type) {
    }

    /**
     * Writes {@code META-INF/dhole/validation.idx}, format version 1 (METADATA_COMPILER.md §34.4):
     * types sorted by name, fields in declaration order.
     */
    static String write(List<ValidationRecord> records) {
        StringBuilder text = new StringBuilder("dhole-validation 1\n");
        records.stream().sorted(java.util.Comparator.comparing(ValidationRecord::type)).forEach(record -> {
            text.append('\n').append("type ").append(record.type()).append('\n');
            record.fields().forEach(field -> text.append("field ").append(field.name()).append(' ')
                    .append(field.type()).append('\n'));
            record.source().ifPresent(source -> text.append("source ").append(source).append('\n'));
        });
        return text.toString();
    }
}
