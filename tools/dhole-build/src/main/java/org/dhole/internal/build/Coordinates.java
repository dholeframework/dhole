package org.dhole.internal.build;

import java.util.Comparator;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The identity of an artifact: {@code group:name:version}.
 */
public record Coordinates(String group, String name, String version) implements Comparable<Coordinates> {

    private static final Pattern PART = Pattern.compile("[A-Za-z0-9._+-]+");
    private static final Comparator<Coordinates> ORDER = Comparator.comparing(Coordinates::group)
            .thenComparing(Coordinates::name).thenComparing(Coordinates::version);

    public Coordinates {
        for (String part : new String[] {group, name, version}) {
            Objects.requireNonNull(part, "coordinates");
            if (!PART.matcher(part).matches()) {
                throw new IllegalArgumentException("Invalid artifact coordinates " + group + ":" + name + ":" + version);
            }
        }
    }

    /**
     * @throws IllegalArgumentException if the text is not {@code group:name:version}
     */
    public static Coordinates parse(String text) {
        String[] parts = text.split(":", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid artifact coordinates " + text);
        }
        return new Coordinates(parts[0], parts[1], parts[2]);
    }

    @Override
    public int compareTo(Coordinates other) {
        return ORDER.compare(this, other);
    }

    @Override
    public String toString() {
        return group + ":" + name + ":" + version;
    }
}
