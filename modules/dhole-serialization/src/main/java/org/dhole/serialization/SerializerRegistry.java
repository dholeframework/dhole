package org.dhole.serialization;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The serializers available to an application, one per media type, in preference order (the first
 * is preferred when a client accepts several).
 */
public final class SerializerRegistry {

    private final Map<MediaType, Serializer> serializers;

    private SerializerRegistry(Map<MediaType, Serializer> serializers) {
        this.serializers = serializers;
    }

    /**
     * @throws IllegalArgumentException if two serializers handle the same media type
     */
    public static SerializerRegistry of(List<Serializer> serializers) {
        Map<MediaType, Serializer> byType = new LinkedHashMap<>();
        for (Serializer serializer : serializers) {
            MediaType type = Objects.requireNonNull(serializer.mediaType(), "mediaType").withoutParameters();
            if (byType.putIfAbsent(type, serializer) != null) {
                throw new IllegalArgumentException("More than one serializer for " + type);
            }
        }
        return new SerializerRegistry(byType);
    }

    /**
     * Returns the media types that can be produced, in preference order.
     */
    public List<MediaType> mediaTypes() {
        return new ArrayList<>(serializers.keySet());
    }

    /**
     * Returns the serializer for a media type, ignoring parameters such as {@code charset}.
     */
    public Optional<Serializer> forMediaType(MediaType type) {
        return Optional.ofNullable(serializers.get(type.withoutParameters()));
    }
}
