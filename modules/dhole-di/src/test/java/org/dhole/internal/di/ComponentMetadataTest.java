package org.dhole.internal.di;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;

import org.dhole.di.DependencyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ComponentMetadataTest {

    private static final ClassLoader LOADER = ComponentMetadataTest.class.getClassLoader();

    @Test
    void parsesTheDocumentedFormat() {
        ComponentMetadata metadata = ComponentMetadata.parse("""
                dhole-metadata 1

                component com.acme.shop.Multiple
                unusable multiple-public-constructors 2
                source com/acme/shop/Multiple.java:3

                component com.acme.shop.Stripe
                constructor com.acme.shop.Client int [Ljava.lang.String;
                supertype com.acme.shop.Gateway
                supertype java.lang.AutoCloseable
                source com/acme/shop/Stripe.java:7

                component com.acme.shop.UserRepository
                constructor
                """, LOADER);

        ComponentMetadata.TypeMetadata stripe = metadata.type("com.acme.shop.Stripe").orElseThrow();
        assertEquals(Optional.of(List.of("com.acme.shop.Client", "int", "[Ljava.lang.String;")), stripe.constructor());
        assertEquals(List.of("com.acme.shop.Gateway", "java.lang.AutoCloseable"), stripe.supertypes());
        assertEquals(Optional.of("com/acme/shop/Stripe.java:7"), stripe.source());
        assertEquals(Optional.of(new ComponentMetadata.Unusable("multiple-public-constructors", 2)),
                metadata.type("com.acme.shop.Multiple").orElseThrow().unusable());
        assertEquals(Optional.of(List.of()), metadata.type("com.acme.shop.UserRepository").orElseThrow().constructor());
        assertEquals(List.of("com.acme.shop.Stripe"), metadata.providersOf("com.acme.shop.Gateway"));
        assertEquals(Optional.empty(), metadata.type("com.acme.shop.Unknown"));
    }

    @Test
    void headerOnlyIsAnEmptyIndex() {
        assertEquals(List.of(), ComponentMetadata.parse("dhole-metadata 1\n", LOADER).providersOf("x.Y"));
    }

    @Test
    void unknownVersionIsACompatibilityError() {
        DependencyException failure = assertThrows(DependencyException.class,
                () -> ComponentMetadata.parse("dhole-metadata 2\n", LOADER));

        assertEquals("Metadata Compatibility Error\n\nApplication metadata version: 2\nRuntime supports: 1\n\n"
                + "Rebuild the application using a compatible Dhole build tool.", failure.getMessage());
    }

    @Test
    void malformedIndexesFailWithTheirLine() {
        assertMalformed("components 1\n", 1, "expected header 'dhole-metadata 1'");
        assertMalformed("dhole-metadata 1\n\nconstructor\n", 3, "expected 'component <type>'");
        assertMalformed("dhole-metadata 1\n\ncomponent a.B\nsupertype a.C\n", 4,
                "component a.B has neither 'constructor' nor 'unusable'");
        assertMalformed("dhole-metadata 1\n\ncomponent a.B\nconstructor\nfactory a.F\n", 5, "unexpected line 'factory a.F'");
        assertMalformed("dhole-metadata 1\n\ncomponent a.B\nconstructor\nsource a/B.java:1\nsupertype a.C\n", 6,
                "unexpected line 'supertype a.C'");
        assertMalformed("dhole-metadata 1\n\ncomponent a.B\nconstructor\n\ncomponent a.B\nconstructor\n", 8,
                "duplicate component a.B");
        assertMalformed("dhole-metadata 1\n\ncomponent a.B\nunusable forgotten\n", 4, "invalid 'unusable' record");
        assertMalformed("dhole-metadata 1\n\ncomponent a.B\nunusable multiple-public-constructors x\n", 4,
                "invalid 'unusable' record");
        assertMalformed("dhole-metadata 1\n\ncomponent a.B\nconstructor a.C\nunusable parameterized-dependency 1\n", 5,
                "invalid 'unusable' record");
        assertMalformed("dhole-metadata 1\n\ncomponent a.B\nconstructor\nunusable not-public\n", 5,
                "invalid 'unusable' record");
        assertMalformed("dhole-metadata 1\n\ncomponent a B\nconstructor\n", 3, "unexpected space in 'a B'");
    }

    @Test
    void missingIndexIsEmpty(@TempDir Path directory) throws IOException {
        try (var loader = new java.net.URLClassLoader(new URL[] {directory.toUri().toURL()}, null)) {
            assertEquals(List.of(), ComponentMetadata.load(loader).providersOf("x.Y"));
        }
    }

    @Test
    void indexIsLoadedFromTheClassLoader(@TempDir Path directory) throws IOException {
        Path index = directory.resolve(ComponentMetadata.LOCATION);
        Files.createDirectories(index.getParent());
        Files.writeString(index, "dhole-metadata 1\n\ncomponent a.B\nconstructor\nsupertype a.C\n", StandardCharsets.UTF_8);

        try (var loader = new java.net.URLClassLoader(new URL[] {directory.toUri().toURL()}, null)) {
            assertEquals(List.of("a.B"), ComponentMetadata.load(loader).providersOf("a.C"));
        }
    }

    @Test
    void severalIndexesAreRejected() {
        ClassLoader twoIndexes = new ClassLoader(null) {
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                URL url = java.net.URI.create("file:/" + name).toURL();
                return Collections.enumeration(List.of(url, url));
            }
        };

        DependencyException failure = assertThrows(DependencyException.class, () -> ComponentMetadata.load(twoIndexes));

        assertTrue(failure.getMessage().contains("Found 2 META-INF/dhole/components.idx files"), failure.getMessage());
    }

    @Test
    void unloadableClassFailsClearly() {
        ComponentMetadata metadata = ComponentMetadata.parse("dhole-metadata 1\n", LOADER);

        DependencyException failure = assertThrows(DependencyException.class, () -> metadata.loadClass("com.example.Gone"));

        assertEquals("Metadata Error\n\nThe component index names com.example.Gone, which cannot be loaded.\n\n"
                + "Rebuild the application.", failure.getMessage());
    }

    @Test
    void loadsPrimitivesArraysAndNestedClasses() {
        ComponentMetadata metadata = ComponentMetadata.parse("dhole-metadata 1\n", LOADER);

        assertEquals(int.class, metadata.loadClass("int"));
        assertEquals(String[].class, metadata.loadClass("[Ljava.lang.String;"));
        assertEquals(int[][].class, metadata.loadClass("[[I"));
        assertEquals(Fixtures.UserService.class, metadata.loadClass("org.dhole.internal.di.Fixtures$UserService"));
    }

    private static void assertMalformed(String text, int line, String problem) {
        DependencyException failure = assertThrows(DependencyException.class, () -> ComponentMetadata.parse(text, LOADER));

        assertEquals("Metadata Error\n\nMalformed META-INF/dhole/components.idx at line " + line + ":\n" + problem
                + "\n\nRebuild the application.", failure.getMessage());
    }
}
