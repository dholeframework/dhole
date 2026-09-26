package org.dhole.internal.compiler;

import java.util.Comparator;
import java.util.List;

/**
 * Writes {@code META-INF/dhole/routes.idx}, format version 1 (METADATA_COMPILER.md §34.3). Output
 * depends only on the records: blocks are sorted by controller, path and method.
 */
final class RouteIndexWriter {

    static final String LOCATION = "META-INF/dhole/routes.idx";
    static final String HEADER = "dhole-routes 1";

    private RouteIndexWriter() {
    }

    static String write(List<RouteRecord> records) {
        StringBuilder text = new StringBuilder(HEADER).append('\n');
        records.stream()
                .sorted(Comparator.comparing(RouteRecord::controller)
                        .thenComparing(RouteRecord::path)
                        .thenComparing(RouteRecord::method))
                .forEach(record -> {
                    text.append('\n').append("route ").append(record.controller()).append(' ')
                            .append(record.method()).append(' ').append(record.path()).append('\n');
                    text.append("handler ").append(record.handler()).append('\n');
                    record.parameters().forEach(parameter -> text.append("parameter ").append(parameter.name())
                            .append(' ').append(parameter.source()).append(' ').append(parameter.type()).append('\n'));
                    text.append("response ").append(record.response()).append('\n');
                    record.source().ifPresent(source -> text.append("source ").append(source).append('\n'));
                });
        return text.toString();
    }
}
