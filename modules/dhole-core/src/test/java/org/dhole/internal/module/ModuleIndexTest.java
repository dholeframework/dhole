package org.dhole.internal.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class ModuleIndexTest {

    private static final String INDEX = """
            dhole-modules 1

            module http

            module routing
            requires http

            module serialization

            module web
            activator org.dhole.internal.web.WebActivator
            requires http routing serialization
            """;

    @Test
    void validIndexIsReadWithActivatorsAndRequirements() {
        ModuleIndex index = ModuleIndex.parse(INDEX);

        assertEquals(List.of(
                new ModuleDescriptor("http", Optional.empty(), List.of()),
                new ModuleDescriptor("routing", Optional.empty(), List.of("http")),
                new ModuleDescriptor("serialization", Optional.empty(), List.of()),
                new ModuleDescriptor("web", Optional.of("org.dhole.internal.web.WebActivator"),
                        List.of("http", "routing", "serialization"))), index.modules());
    }

    @Test
    void activationOrderPutsRequirementsFirstAndBreaksTiesById() {
        ModuleIndex index = ModuleIndex.parse("""
                dhole-modules 1

                module a
                requires c

                module b

                module c
                requires b
                """);

        assertEquals(List.of("b", "c", "a"), index.activationOrder().stream().map(ModuleDescriptor::id).toList());
        assertEquals(List.of("http", "routing", "serialization", "web"),
                ModuleIndex.parse(INDEX).activationOrder().stream().map(ModuleDescriptor::id).toList());
    }

    @Test
    void formatIsDeterministicAndRoundTrips() {
        List<ModuleDescriptor> modules = ModuleIndex.parse(INDEX).modules();
        List<ModuleDescriptor> reversed = modules.reversed();

        assertEquals(INDEX, ModuleIndex.format(modules));
        assertEquals(INDEX, ModuleIndex.format(reversed));
        assertEquals("dhole-modules 1\n", ModuleIndex.format(List.of()));
    }

    @Test
    void unknownVersionIsACompatibilityError() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> ModuleIndex.parse("dhole-modules 2\n"));

        assertTrue(failure.getMessage().startsWith("Metadata Compatibility Error"), failure.getMessage());
        assertTrue(failure.getMessage().endsWith("Rebuild the application."), failure.getMessage());
    }

    @Test
    void malformedIndexesAreRejectedWithTheirLine() {
        for (String text : List.of("", "modules 1\n", "dhole-modules 1\nmodule a\n",
                "dhole-modules 1\n\nactivator a.B\n", "dhole-modules 1\n\nmodule A\n",
                "dhole-modules 1\n\nmodule a\nactivator not a class\n", "dhole-modules 1\n\nmodule a\nrequires a\n",
                "dhole-modules 1\n\nmodule a\nrequires b b\n", "dhole-modules 1\n\nmodule a\nextra\n",
                "dhole-modules 1\n\nmodule a\nrequires b\nactivator a.B\n")) {
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> ModuleIndex.parse(text), text);
            assertTrue(failure.getMessage().startsWith("Metadata Error\n\nMalformed META-INF/dhole/modules.idx at line "),
                    failure.getMessage());
        }
    }

    @Test
    void duplicateModuleIsRejected() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> ModuleIndex.parse("dhole-modules 1\n\nmodule a\n\nmodule a\n"));

        assertTrue(failure.getMessage().contains("at line 5: module 'a' is declared more than once"), failure.getMessage());
    }

    @Test
    void missingRequiredModuleFailsActivation() {
        ModuleIndex index = ModuleIndex.parse("dhole-modules 1\n\nmodule web\nrequires routing\n");

        IllegalStateException failure = assertThrows(IllegalStateException.class, index::activationOrder);

        assertTrue(failure.getMessage().startsWith("Module Dependency Error\n\nModule 'web' requires module 'routing'"),
                failure.getMessage());
    }

    @Test
    void circularRequirementsAreReportedAsAPath() {
        ModuleIndex index = ModuleIndex.parse("""
                dhole-modules 1

                module module-a
                requires module-b

                module module-b
                requires module-a
                """);

        IllegalStateException failure = assertThrows(IllegalStateException.class, index::activationOrder);

        assertEquals("""
                Module Dependency Error

                Circular dependency detected:
                module-a
                  -> module-b
                      -> module-a""", failure.getMessage());
    }

    @Test
    void descriptorDeclaresExactlyOneModule() {
        assertEquals(new ModuleDescriptor("json", Optional.empty(), List.of("serialization")),
                ModuleIndex.parseDescriptor("dhole-module 1\n\nmodule json\nrequires serialization\n"));
        assertThrows(IllegalStateException.class, () -> ModuleIndex.parseDescriptor("dhole-module 1\n"));
        assertThrows(IllegalStateException.class, () -> ModuleIndex.parseDescriptor("dhole-modules 1\n\nmodule a\n"));
    }
}
