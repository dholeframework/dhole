package org.dhole;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Verifies that the repository build and test infrastructure work.
 *
 * <p>This test does not exercise any Dhole runtime behavior.
 */
class BuildInfrastructureSmokeTest {

    @Test
    void testsRunOnTheJava21Toolchain() {
        assertEquals(21, Runtime.version().feature());
    }
}
