package org.dhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DholeTest {

    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
    private PrintStream originalOut;

    @BeforeEach
    void captureStandardOutput() {
        originalOut = System.out;
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreStandardOutput() {
        System.setOut(originalOut);
    }

    @Test
    void runBootstrapsAndStartsTheApplication() {
        Dhole.run(DholeTest.class);

        assertEquals(List.of("Dhole", "", "Application starting...", "Application ready."),
                captured.toString(StandardCharsets.UTF_8).lines().toList());
    }

    @Test
    void runRequiresAnApplicationClass() {
        assertThrows(NullPointerException.class, () -> Dhole.run(null));
    }
}
