package org.dhole.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class BindingWrappersTest {

    @Test
    void presentValuesAreReturned() {
        assertEquals(7L, Path.of(7L).value());
        assertEquals("java", Query.of("term", "java").value());
        assertEquals(Optional.of("pt"), Header.of("acceptLanguage", "pt").optional());
        assertEquals("input", Body.of("input").value());
    }

    @Test
    void missingOptionalValuesAreEmptyAndValueFailsWithTheParameterName() {
        assertEquals(Optional.empty(), Query.of("page", null).optional());

        BindingException query = assertThrows(BindingException.class, () -> Query.of("page", null).value());
        BindingException header = assertThrows(BindingException.class, () -> Header.of("acceptLanguage", null).value());

        assertEquals("Missing query parameter 'page'.", query.getMessage());
        assertEquals("Missing header parameter 'acceptLanguage'.", header.getMessage());
    }
}
