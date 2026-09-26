package org.dhole.internal.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.dhole.serialization.MediaType;
import org.dhole.serialization.SerializationException;
import org.dhole.serialization.TypeRef;
import org.junit.jupiter.api.Test;

class JacksonJsonSerializerTest {

    private final JacksonJsonSerializer json = new JacksonJsonSerializer();

    public enum Status {
        PENDING,
        APPROVED
    }

    public record Address(String city) {
    }

    public record User(long id, String name, String email, Status status, Address address) {
    }

    public record Event(LocalDate day, Instant at, Duration length) {
    }

    public record Counter(int count, Double ratio, boolean active) {
    }

    @Test
    void mediaTypeIsJson() {
        assertEquals(MediaType.APPLICATION_JSON, json.mediaType());
    }

    @Test
    void recordIsWrittenAsJson() {
        User user = new User(1, "Mamadu", "mamadu@example.com", Status.PENDING, new Address("Lisboa"));

        assertEquals("{\"id\":1,\"name\":\"Mamadu\",\"email\":\"mamadu@example.com\",\"status\":\"PENDING\","
                + "\"address\":{\"city\":\"Lisboa\"}}", write(user, TypeRef.of(User.class)));
    }

    @Test
    void jsonIsReadIntoANestedRecord() {
        User user = read("{\"id\":1,\"name\":\"Mamadu\",\"email\":\"m@example.com\",\"status\":\"APPROVED\","
                + "\"address\":{\"city\":\"Porto\"}}", TypeRef.of(User.class));

        assertEquals(new User(1, "Mamadu", "m@example.com", Status.APPROVED, new Address("Porto")), user);
    }

    @Test
    void genericTypesAreReadAndWrittenThroughTypeRef() {
        TypeRef<List<Address>> addresses = new TypeRef<>() {
        };
        TypeRef<Map<String, List<Integer>>> scores = new TypeRef<>() {
        };

        assertEquals(List.of(new Address("A"), new Address("B")), read("[{\"city\":\"A\"},{\"city\":\"B\"}]", addresses));
        assertEquals("[{\"city\":\"A\"}]", write(List.of(new Address("A")), addresses));
        assertEquals(Map.of("x", List.of(1, 2)), read("{\"x\":[1,2]}", scores));
    }

    @Test
    void javaTimeUsesIsoText() {
        Event event = new Event(LocalDate.of(2026, 9, 25), Instant.parse("2026-09-25T10:15:30Z"), Duration.ofMinutes(90));

        String text = write(event, TypeRef.of(Event.class));

        assertEquals("{\"day\":\"2026-09-25\",\"at\":\"2026-09-25T10:15:30Z\",\"length\":\"PT1H30M\"}", text);
        assertEquals(event, read(text, TypeRef.of(Event.class)));
    }

    @Test
    void explicitNullIsAcceptedForReferencesButMissingPropertiesFail() {
        User user = read("{\"id\":1,\"name\":null,\"email\":\"e\",\"status\":\"PENDING\",\"address\":null}",
                TypeRef.of(User.class));
        assertNull(user.name());

        assertFailure("{\"id\":1,\"email\":\"e\",\"status\":\"PENDING\",\"address\":null}", TypeRef.of(User.class),
                "Invalid or missing JSON value at '$.name'");
        assertFailure("{\"count\":null,\"ratio\":1.0,\"active\":true}", TypeRef.of(Counter.class),
                "Invalid or missing JSON value at '$.count'");
    }

    @Test
    void malformedJsonFails() {
        assertFailure("{\"city\":", TypeRef.of(Address.class), "Malformed JSON");
        assertFailure("{\"city\":\"A\"} trailing", TypeRef.of(Address.class), "Malformed JSON");
        assertFailure("{\"city\":\"A\",\"city\":\"B\"}", TypeRef.of(Address.class), "Malformed JSON");
        assertThrows(SerializationException.class, () -> read("", TypeRef.of(Address.class)));
    }

    @Test
    void unknownPropertiesFail() {
        assertFailure("{\"city\":\"A\",\"country\":\"PT\"}", TypeRef.of(Address.class), "Unknown JSON property '$.country'");
    }

    @Test
    void incompatibleTypesAndCoercionsFail() {
        TypeRef<Counter> counter = TypeRef.of(Counter.class);
        assertEquals(new Counter(3, 1.0, true), read("{\"count\":3,\"ratio\":1,\"active\":true}", counter));

        assertFailure("{\"count\":\"3\",\"ratio\":1.0,\"active\":true}", counter, "'$.count'");
        assertFailure("{\"count\":3.5,\"ratio\":1.0,\"active\":true}", counter, "'$.count'");
        assertFailure("{\"count\":3,\"ratio\":\"1.0\",\"active\":true}", counter, "'$.ratio'");
        assertFailure("{\"count\":3,\"ratio\":1.0,\"active\":1}", counter, "'$.active'");
        assertFailure("{\"count\":3,\"ratio\":1.0,\"active\":\"true\"}", counter, "'$.active'");
        assertFailure("{\"city\":5}", TypeRef.of(Address.class), "'$.city'");
        assertFailure("{\"city\":true}", TypeRef.of(Address.class), "'$.city'");
        assertFailure("{\"city\":[\"A\"]}", TypeRef.of(Address.class), "'$.city'");
    }

    @Test
    void invalidEnumsFail() {
        TypeRef<List<Status>> statuses = new TypeRef<>() {
        };

        assertEquals(List.of(Status.PENDING), read("[\"PENDING\"]", statuses));
        assertFailure("[\"UNKNOWN\"]", statuses, "'$[0]'");
        assertFailure("[\"pending\"]", statuses, "'$[0]'");
        assertFailure("[0]", statuses, "'$[0]'");
    }

    @Test
    void failuresNeverContainInputValuesAndKeepTheCause() {
        SerializationException failure = assertThrows(SerializationException.class,
                () -> read("{\"count\":\"secret-value\",\"ratio\":1.0,\"active\":true}", TypeRef.of(Counter.class)));

        assertFalse(failure.getMessage().contains("secret-value"), failure.getMessage());
        assertFalse(failure.getMessage().contains("jackson"), failure.getMessage());
        assertNotNull(failure.getCause());
    }

    @Test
    void polymorphicTypeNamesInJsonAreNotHonoured() {
        TypeRef<Object> anything = TypeRef.of(Object.class);

        Object value = read("[\"java.util.ArrayList\",[\"x\"]]", anything);

        assertEquals(List.of("java.util.ArrayList", List.of("x")), value);
    }

    @Test
    void streamsAreNotClosed() {
        ClosingTracker input = new ClosingTracker("{\"city\":\"A\"}".getBytes(StandardCharsets.UTF_8));
        json.deserialize(input, TypeRef.of(Address.class));
        assertFalse(input.closed);
    }

    @Test
    void publicSignaturesExposeNoJacksonTypes() {
        for (Method method : JacksonJsonSerializer.class.getMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            assertFalse(method.getReturnType().getName().startsWith("com.fasterxml"), method.toString());
            assertTrue(Arrays.stream(method.getParameterTypes()).noneMatch(type -> type.getName().startsWith("com.fasterxml")),
                    method.toString());
        }
    }

    private <T> T read(String text, TypeRef<T> type) {
        return json.deserialize(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), type);
    }

    private String write(Object value, TypeRef<?> type) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        json.serialize(value, type, output);
        return output.toString(StandardCharsets.UTF_8);
    }

    private void assertFailure(String text, TypeRef<?> type, String expected) {
        SerializationException failure = assertThrows(SerializationException.class, () -> read(text, type), text);
        assertTrue(failure.getMessage().contains(expected), text + " -> " + failure.getMessage());
    }

    private static final class ClosingTracker extends ByteArrayInputStream {

        private boolean closed;

        ClosingTracker(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
