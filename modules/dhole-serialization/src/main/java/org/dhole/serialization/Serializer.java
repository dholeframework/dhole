package org.dhole.serialization;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Converts between Java values and one media type.
 */
public interface Serializer {

    /**
     * Returns the media type this serializer reads and writes, for example {@code application/json}.
     */
    MediaType mediaType();

    /**
     * Reads a value of {@code type}.
     *
     * @throws SerializationException if the input is malformed or does not match {@code type}
     */
    <T> T deserialize(InputStream input, TypeRef<T> type);

    /**
     * Writes {@code value}, described by {@code type}.
     *
     * @throws SerializationException if the value cannot be written
     */
    void serialize(Object value, TypeRef<?> type, OutputStream output);
}
