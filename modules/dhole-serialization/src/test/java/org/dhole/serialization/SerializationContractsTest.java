package org.dhole.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class SerializationContractsTest {

    private static final List<MediaType> JSON_THEN_TEXT = List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN);

    @Test
    void parsesMediaTypesCaseInsensitivelyWithParameters() {
        MediaType type = MediaType.parse("Application/JSON; Charset=\"UTF-8\"");

        assertEquals("application", type.type());
        assertEquals("json", type.subtype());
        assertEquals(Optional.of("UTF-8"), type.parameter("charset"));
        assertEquals(MediaType.APPLICATION_JSON, type.withoutParameters());
        assertEquals("application/json; charset=UTF-8", type.toString());
    }

    @Test
    void rejectsMalformedMediaTypes() {
        for (String text : List.of("json", "application/", "/json", "*/json", "text/plain; charset", "a b/c")) {
            assertThrows(IllegalArgumentException.class, () -> MediaType.parse(text), text);
        }
    }

    @Test
    void rangesIncludeMatchingTypes() {
        assertTrue(MediaType.parse("*/*").includes(MediaType.APPLICATION_JSON));
        assertTrue(MediaType.parse("application/*").includes(MediaType.APPLICATION_JSON));
        assertTrue(MediaType.parse("application/json; q=0.5").includes(MediaType.APPLICATION_JSON));
        assertFalse(MediaType.parse("text/*").includes(MediaType.APPLICATION_JSON));
    }

    @Test
    void selectsByQualityThenProducibleOrder() {
        assertEquals(Optional.of(MediaType.APPLICATION_JSON), MediaType.select(null, JSON_THEN_TEXT));
        assertEquals(Optional.of(MediaType.APPLICATION_JSON), MediaType.select("  ", JSON_THEN_TEXT));
        assertEquals(Optional.of(MediaType.APPLICATION_JSON), MediaType.select("*/*", JSON_THEN_TEXT));
        assertEquals(Optional.of(MediaType.TEXT_PLAIN), MediaType.select("text/plain", JSON_THEN_TEXT));
        assertEquals(Optional.of(MediaType.TEXT_PLAIN),
                MediaType.select("application/json;q=0.2, text/*;q=0.8", JSON_THEN_TEXT));
        assertEquals(Optional.of(MediaType.TEXT_PLAIN),
                MediaType.select("*/*, application/json;q=0", JSON_THEN_TEXT));
        assertEquals(Optional.empty(), MediaType.select("application/xml", JSON_THEN_TEXT));
        assertEquals(Optional.empty(), MediaType.select("application/json;q=0", List.of(MediaType.APPLICATION_JSON)));
        assertEquals(Optional.of(MediaType.APPLICATION_JSON),
                MediaType.select("not a type, application/json", JSON_THEN_TEXT));
    }

    @Test
    void typeRefCapturesGenericTypes() {
        TypeRef<List<Map<String, Integer>>> ref = new TypeRef<>() {
        };

        ParameterizedType type = (ParameterizedType) ref.type();
        assertEquals(List.class, type.getRawType());
        assertEquals(List.class, ref.rawType());
        assertEquals("java.util.List<java.util.Map<java.lang.String, java.lang.Integer>>", ref.toString());
        assertEquals(new TypeRef<List<Map<String, Integer>>>() {
        }, ref);
        assertEquals(String.class, TypeRef.of(String.class).type());
        assertEquals(TypeRef.of(ref.type()), ref);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void typeRefWithoutTypeArgumentIsRejected() {
        assertThrows(IllegalStateException.class, () -> new TypeRef() {
        });
    }

    @Test
    void registryFindsSerializersByMediaTypeIgnoringParameters() {
        Serializer json = new FakeSerializer(MediaType.APPLICATION_JSON);
        SerializerRegistry registry = SerializerRegistry.of(List.of(json));

        assertSame(json, registry.forMediaType(MediaType.parse("application/json; charset=utf-8")).orElseThrow());
        assertEquals(Optional.empty(), registry.forMediaType(MediaType.TEXT_PLAIN));
        assertEquals(List.of(MediaType.APPLICATION_JSON), registry.mediaTypes());
        assertThrows(IllegalArgumentException.class, () -> SerializerRegistry.of(List.of(json, json)));
    }

    private record FakeSerializer(MediaType mediaType) implements Serializer {

        @Override
        public <T> T deserialize(InputStream input, TypeRef<T> type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void serialize(Object value, TypeRef<?> type, OutputStream output) {
            throw new UnsupportedOperationException();
        }
    }
}
