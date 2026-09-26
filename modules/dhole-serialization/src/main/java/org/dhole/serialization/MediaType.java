package org.dhole.serialization;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A media type such as {@code application/json} or {@code text/plain; charset=UTF-8}. Type,
 * subtype and parameter names are case-insensitive and kept in lower case.
 */
public final class MediaType {

    public static final MediaType APPLICATION_JSON = new MediaType("application", "json", Map.of());
    public static final MediaType TEXT_PLAIN = new MediaType("text", "plain", Map.of());

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9!#$%&'*+.^_`|~-]+");

    private final String type;
    private final String subtype;
    private final Map<String, String> parameters;

    private MediaType(String type, String subtype, Map<String, String> parameters) {
        this.type = type;
        this.subtype = subtype;
        this.parameters = Map.copyOf(parameters);
    }

    /**
     * Parses a media type or media range.
     *
     * @throws IllegalArgumentException if the text is not a media type
     */
    public static MediaType parse(String text) {
        Objects.requireNonNull(text, "text");
        String[] parts = text.split(";");
        String[] names = parts[0].strip().split("/", -1);
        if (names.length != 2 || !TOKEN.matcher(names[0]).matches() || !TOKEN.matcher(names[1]).matches()
                || (names[0].equals("*") && !names[1].equals("*"))) {
            throw new IllegalArgumentException("Invalid media type: " + text);
        }
        Map<String, String> parameters = new LinkedHashMap<>();
        for (int index = 1; index < parts.length; index++) {
            String[] parameter = parts[index].strip().split("=", 2);
            if (parameter.length != 2 || !TOKEN.matcher(parameter[0]).matches() || parameter[1].isEmpty()) {
                throw new IllegalArgumentException("Invalid media type parameter: " + text);
            }
            String value = parameter[1].strip();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            parameters.put(parameter[0].toLowerCase(Locale.ROOT), value);
        }
        return new MediaType(names[0].toLowerCase(Locale.ROOT), names[1].toLowerCase(Locale.ROOT), parameters);
    }

    /**
     * Selects the media type to produce for an {@code Accept} header.
     *
     * <p>Each producible type gets the quality of the most specific acceptable range that matches it
     * (exact, then {@code type/*}, then {@code *}{@code /*}); ranges with {@code q=0} exclude it.
     * The highest quality wins; ties go to the earlier producible type. A missing or blank header
     * accepts everything; malformed ranges are ignored.
     *
     * @return the selected type, or empty when nothing producible is acceptable (406)
     */
    public static Optional<MediaType> select(String accept, List<MediaType> producible) {
        Objects.requireNonNull(producible, "producible");
        if (accept == null || accept.isBlank()) {
            return producible.stream().findFirst();
        }
        List<Range> ranges = Range.parseAll(accept);
        MediaType best = null;
        double bestQuality = 0;
        for (MediaType candidate : producible) {
            double quality = Range.quality(ranges, candidate);
            if (quality > bestQuality) {
                best = candidate;
                bestQuality = quality;
            }
        }
        return Optional.ofNullable(best);
    }

    public String type() {
        return type;
    }

    public String subtype() {
        return subtype;
    }

    public Optional<String> parameter(String name) {
        return Optional.ofNullable(parameters.get(name.toLowerCase(Locale.ROOT)));
    }

    /**
     * Whether this media range includes {@code other}, ignoring parameters: {@code *}{@code /*},
     * {@code text/*} includes {@code text/plain}, and an exact type includes itself.
     */
    public boolean includes(MediaType other) {
        if (type.equals("*")) {
            return true;
        }
        return type.equals(other.type) && (subtype.equals("*") || subtype.equals(other.subtype));
    }

    /**
     * Returns this type without parameters.
     */
    public MediaType withoutParameters() {
        return parameters.isEmpty() ? this : new MediaType(type, subtype, Map.of());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MediaType media && type.equals(media.type) && subtype.equals(media.subtype)
                && parameters.equals(media.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, subtype, parameters);
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder(type).append('/').append(subtype);
        parameters.forEach((name, value) -> text.append("; ").append(name).append('=').append(value));
        return text.toString();
    }

    private record Range(MediaType type, double quality) {

        static List<Range> parseAll(String header) {
            return Arrays.stream(header.split(","))
                    .map(String::strip)
                    .filter(text -> !text.isEmpty())
                    .flatMap(text -> parse(text).stream())
                    .toList();
        }

        static Optional<Range> parse(String text) {
            try {
                MediaType range = MediaType.parse(text);
                double quality = range.parameter("q").map(Double::parseDouble).orElse(1.0);
                if (quality < 0 || quality > 1) {
                    return Optional.empty();
                }
                return Optional.of(new Range(range, quality));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }

        static double quality(List<Range> ranges, MediaType candidate) {
            Range best = null;
            for (Range range : ranges) {
                if (range.type.includes(candidate) && (best == null || specificity(range) > specificity(best))) {
                    best = range;
                }
            }
            return best == null ? 0 : best.quality;
        }

        private static int specificity(Range range) {
            if (range.type.type.equals("*")) {
                return 0;
            }
            return range.type.subtype.equals("*") ? 1 : 2;
        }
    }
}
