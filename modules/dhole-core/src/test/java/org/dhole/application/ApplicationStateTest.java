package org.dhole.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class ApplicationStateTest {

    @Test
    void definesExactlyTheSpecifiedLifecycleStates() {
        Set<String> states = Arrays.stream(ApplicationState.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertEquals(Set.of("CREATED", "STARTING", "RUNNING", "STOPPING", "STOPPED", "FAILED"), states);
    }
}
