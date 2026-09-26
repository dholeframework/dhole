package org.dhole.internal.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ConversionServiceTest {

    enum Status {
        ACTIVE
    }

    @Test
    void convertsEveryBuiltInScalar() {
        assertEquals("x", ConversionService.convert("x", String.class));
        assertEquals((byte) 7, ConversionService.convert("7", byte.class));
        assertEquals((short) -7, ConversionService.convert("-7", Short.class));
        assertEquals(42, ConversionService.convert("42", int.class));
        assertEquals(42L, ConversionService.convert("42", long.class));
        assertEquals(1.5f, ConversionService.convert("1.5", Float.class));
        assertEquals(2.5, ConversionService.convert("2.5", double.class));
        assertEquals(true, ConversionService.convert("true", boolean.class));
        assertEquals(false, ConversionService.convert("false", Boolean.class));
        UUID id = UUID.randomUUID();
        assertEquals(id, ConversionService.convert(id.toString(), UUID.class));
        assertEquals(Status.ACTIVE, ConversionService.convert("ACTIVE", Status.class));
        assertEquals(LocalDate.of(2026, 9, 25), ConversionService.convert("2026-09-25", LocalDate.class));
        assertEquals(LocalDateTime.of(2026, 9, 25, 10, 15), ConversionService.convert("2026-09-25T10:15", LocalDateTime.class));
        assertEquals(Instant.parse("2026-09-25T10:15:30Z"), ConversionService.convert("2026-09-25T10:15:30Z", Instant.class));
    }

    @Test
    void rejectsInvalidValuesStrictly() {
        Map<String, Class<?>> invalid = Map.ofEntries(
                Map.entry("abc", long.class), Map.entry("1.5", int.class), Map.entry("99999999999", int.class),
                Map.entry(" 42", long.class), Map.entry("300", byte.class), Map.entry("yes", boolean.class),
                Map.entry("TRUE", Boolean.class), Map.entry("NaN", double.class), Map.entry("Infinity", Float.class),
                Map.entry("not-a-uuid", UUID.class), Map.entry("active", Status.class),
                Map.entry("25/09/2026", LocalDate.class), Map.entry("2026-09-25", Instant.class));
        invalid.forEach((value, type) -> assertThrows(IllegalArgumentException.class,
                () -> ConversionService.convert(value, type), value + " -> " + type));
    }

    @Test
    void supportsOnlyTheBuiltInScalars() {
        for (Class<?> type : List.of(String.class, long.class, Long.class, Status.class, UUID.class, Instant.class)) {
            assertTrue(ConversionService.supports(type), type.getName());
        }
        for (Class<?> type : List.of(char.class, Object.class, List.class, StringBuilder.class)) {
            assertFalse(ConversionService.supports(type), type.getName());
        }
    }
}
