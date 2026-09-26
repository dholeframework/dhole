package org.dhole.internal.json;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

import org.dhole.serialization.MediaType;
import org.dhole.serialization.SerializationException;
import org.dhole.serialization.Serializer;
import org.dhole.serialization.TypeRef;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * The official JSON {@link Serializer}, over Jackson. Jackson is an implementation detail: no
 * Jackson type crosses this class's API, and Jackson exceptions are translated to
 * {@link SerializationException} with messages that name properties but never contain input values.
 *
 * <p>Configured explicitly and strictly: malformed JSON, trailing tokens, duplicate properties,
 * unknown properties, missing record/creator properties, {@code null} for primitives, invalid enum
 * names, numbers for enums, fractional numbers for integers and scalar coercions (strings to numbers
 * or booleans, numbers or booleans to strings, numbers to booleans) all fail. {@code java.time}
 * values use ISO-8601 text. No default typing: class names in JSON are never loaded. Streams are
 * never closed by the serializer.
 *
 * <p>Public only for the internal web runtime; not application API.
 */
public final class JacksonJsonSerializer implements Serializer {

    private final ObjectMapper mapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(JsonParser.Feature.AUTO_CLOSE_SOURCE)
            .disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
            .withCoercionConfigDefaults(config -> config
                    .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail))
            .withCoercionConfig(LogicalType.Textual, config -> config
                    .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
            .withCoercionConfig(LogicalType.Boolean, config -> config
                    .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail))
            .build();

    @Override
    public MediaType mediaType() {
        return MediaType.APPLICATION_JSON;
    }

    @Override
    public <T> T deserialize(InputStream input, TypeRef<T> type) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(type, "type");
        JavaType javaType = mapper.constructType(type.type());
        try {
            @SuppressWarnings("unchecked")
            T value = (T) mapper.readValue(input, javaType);
            return value;
        } catch (UnrecognizedPropertyException e) {
            throw new SerializationException("Unknown JSON property '" + path(e) + "'", e);
        } catch (DatabindException e) {
            throw new SerializationException("Invalid or missing JSON value at '" + path((JsonMappingException) e)
                    + "' for " + type, e);
        } catch (StreamReadException e) {
            throw new SerializationException("Malformed JSON", e);
        } catch (IOException e) {
            throw new SerializationException("Unable to read JSON", e);
        }
    }

    @Override
    public void serialize(Object value, TypeRef<?> type, OutputStream output) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(output, "output");
        try {
            mapper.writerFor(mapper.constructType(type.type())).writeValue(output, value);
        } catch (IOException e) {
            throw new SerializationException("Unable to write " + type + " as JSON", e);
        }
    }

    /**
     * Returns the property path of a mapping failure, for example {@code $.items[0].name}; names
     * only, never values.
     */
    private static String path(JsonMappingException exception) {
        StringBuilder path = new StringBuilder("$");
        for (JsonMappingException.Reference reference : exception.getPath()) {
            if (reference.getFieldName() != null) {
                path.append('.').append(reference.getFieldName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }
}
