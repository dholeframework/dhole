package org.dhole.internal.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MetadataProcessorTest {

    @TempDir
    Path output;

    // Application root

    @Test
    void validProjectProducesComponentMetadata() {
        TestCompiler.Result result = shop().compile(output);

        assertTrue(result.success(), result.diagnostics().toString());
        assertEquals(String.join("\n",
                "dhole-metadata 1",
                "",
                "component com.acme.shop.App",
                "unusable no-public-constructor",
                "source com/acme/shop/App.java:3",
                "",
                "component com.acme.shop.users.UserRepository",
                "constructor",
                "source com/acme/shop/users/UserRepository.java:3",
                "",
                "component com.acme.shop.users.UserService",
                "constructor com.acme.shop.users.UserRepository",
                "source com/acme/shop/users/UserService.java:6",
                ""), result.index().orElseThrow());
    }

    @Test
    void typesOutsideTheApplicationRootAreExcluded() {
        TestCompiler.Result result = shop()
                .source("com.acme.other.Outside", "package com.acme.other; public class Outside {}")
                .source("com.acme.shopping.Sibling", "package com.acme.shopping; public class Sibling {}")
                .compile(output);

        String index = result.index().orElseThrow();
        assertFalse(index.contains("Outside"), index);
        assertFalse(index.contains("Sibling"), index);
    }

    @Test
    void missingApplicationOptionIsALibraryCompilation() {
        TestCompiler.Result result = TestCompiler.create()
                .source("com.acme.shop.users.UserRepository", USER_REPOSITORY)
                .compile(output);

        assertTrue(result.success(), result.diagnostics().toString());
        assertEquals(Optional.empty(), result.index());
    }

    @Test
    void nonexistentApplicationClassFailsClearly() {
        TestCompiler.Result result = TestCompiler.create()
                .source("com.acme.shop.users.UserRepository", USER_REPOSITORY)
                .application("com.acme.shop.Missing")
                .compile(output);

        assertFalse(result.success());
        assertEquals(List.of("ERROR: Dhole Error DHOLE-META-001: Application class com.acme.shop.Missing "
                + "configured with -Adhole.application does not exist"), result.diagnostics());
        assertEquals(Optional.empty(), result.index());
    }

    @Test
    void malformedApplicationClassNameFailsClearly() {
        TestCompiler.Result result = TestCompiler.create()
                .source("com.acme.shop.users.UserRepository", USER_REPOSITORY)
                .application("com.acme..App")
                .compile(output);

        assertFalse(result.success());
        assertTrue(result.diagnostics().get(0).contains("DHOLE-META-001: Invalid -Adhole.application value 'com.acme..App'"),
                result.diagnostics().toString());
    }

    @Test
    void otherMainMethodsAndDholeRunCallsDoNotAffectTheRoot() {
        TestCompiler.Result result = shop()
                .source("com.acme.Launcher", """
                        package com.acme;
                        public class Launcher {
                            public static void main(String[] args) { com.acme.shop.App.main(args); }
                        }
                        """)
                .source("com.acme.tools.Tool", """
                        package com.acme.tools;
                        public class Tool {
                            public static void main(String[] args) { }
                        }
                        """)
                .compile(output);

        String index = result.index().orElseThrow();
        assertFalse(index.contains("Launcher"), index);
        assertFalse(index.contains("Tool"), index);
        assertTrue(index.contains("component com.acme.shop.App"), index);
    }

    @Test
    void emptyApplicationContainsOnlyTheApplicationClass() {
        TestCompiler.Result result = TestCompiler.create()
                .source("com.acme.shop.App", "package com.acme.shop; public class App { }")
                .application("com.acme.shop.App")
                .compile(output);

        assertEquals("dhole-metadata 1\n\ncomponent com.acme.shop.App\nconstructor\nsource com/acme/shop/App.java:1\n",
                result.index().orElseThrow());
    }

    // Constructor analysis

    @Test
    void constructorDependenciesUseClassForNameFormat() {
        TestCompiler.Result result = app().source("com.acme.shop.Mixed", """
                package com.acme.shop;
                public class Mixed {
                    public Mixed(int count, String[] names, int[][] grid, Inner inner, java.util.List<String> raw) { }
                    public static class Inner { }
                }
                """).compile(output);

        String index = result.index().orElseThrow();
        assertTrue(index.contains("component com.acme.shop.Mixed\n"
                + "constructor int [Ljava.lang.String; [[I com.acme.shop.Mixed$Inner java.util.List\n"
                + "unusable parameterized-dependency 4\n"), index);
        assertTrue(index.contains("component com.acme.shop.Mixed$Inner\nconstructor\n"), index);
    }

    @Test
    void structuralProblemsAreRecordedAsDataNotErrors() {
        TestCompiler.Result result = app()
                .source("com.acme.shop.Multiple", """
                        package com.acme.shop;
                        public class Multiple {
                            public Multiple() { }
                            public Multiple(String name) { }
                        }
                        """)
                .source("com.acme.shop.Hidden", "package com.acme.shop; class Hidden { }")
                .source("com.acme.shop.PrivateOnly", """
                        package com.acme.shop;
                        public class PrivateOnly { private PrivateOnly() { } }
                        """)
                .source("com.acme.shop.Outer", """
                        package com.acme.shop;
                        public class Outer { public class Inner { } }
                        """)
                .compile(output);

        assertTrue(result.success(), result.diagnostics().toString());
        assertEquals(List.of(), result.diagnostics());
        String index = result.index().orElseThrow();
        assertTrue(index.contains("component com.acme.shop.Multiple\nunusable multiple-public-constructors 2\n"), index);
        assertTrue(index.contains("component com.acme.shop.Hidden\nunusable not-public\n"), index);
        assertTrue(index.contains("component com.acme.shop.PrivateOnly\nunusable no-public-constructor\n"), index);
        assertTrue(index.contains("component com.acme.shop.Outer$Inner\nunusable not-static-nested\n"), index);
    }

    @Test
    void interfacesAbstractClassesAndEnumsAreNotIndexedButRecordsAre() {
        TestCompiler.Result result = app()
                .source("com.acme.shop.Gateway", "package com.acme.shop; public interface Gateway { }")
                .source("com.acme.shop.Base", "package com.acme.shop; public abstract class Base { }")
                .source("com.acme.shop.Color", "package com.acme.shop; public enum Color { RED }")
                .source("com.acme.shop.Point", "package com.acme.shop; public record Point(int x, int y) { }")
                .compile(output);

        String index = result.index().orElseThrow();
        assertFalse(index.contains("component com.acme.shop.Gateway"), index);
        assertFalse(index.contains("component com.acme.shop.Base"), index);
        assertFalse(index.contains("component com.acme.shop.Color"), index);
        assertTrue(index.contains("component com.acme.shop.Point\nconstructor int int\nsupertype java.lang.Record\n"),
                index);
    }

    @Test
    void transitiveSupertypesAreRecordedSortedWithoutObject() {
        TestCompiler.Result result = app()
                .source("com.acme.shop.Gateway", "package com.acme.shop; public interface Gateway extends AutoCloseable { }")
                .source("com.acme.shop.Base", "package com.acme.shop; public abstract class Base<T> implements Gateway { }")
                .source("com.acme.shop.Stripe", """
                        package com.acme.shop;
                        public class Stripe extends Base<String> implements java.io.Serializable {
                            public void close() { }
                        }
                        """)
                .compile(output);

        assertTrue(result.index().orElseThrow().contains("component com.acme.shop.Stripe\nconstructor\n"
                + "supertype com.acme.shop.Base\nsupertype com.acme.shop.Gateway\n"
                + "supertype java.io.Serializable\nsupertype java.lang.AutoCloseable\n"), result.index().orElseThrow());
    }

    // Determinism, boundaries and safety

    @Test
    void outputIsByteForByteDeterministic(@TempDir Path first, @TempDir Path second) throws IOException {
        shop().compile(first);
        TestCompiler reordered = TestCompiler.create()
                .source("com.acme.shop.users.UserService", USER_SERVICE)
                .source("com.acme.shop.App", APP)
                .source("com.acme.shop.users.UserRepository", USER_REPOSITORY)
                .application("com.acme.shop.App");
        reordered.compile(second);

        assertEquals(-1L, Files.mismatch(first.resolve(ComponentIndexWriter.LOCATION),
                second.resolve(ComponentIndexWriter.LOCATION)));
    }

    @Test
    void compiledLibrariesOnTheClasspathAreNotIndexed(@TempDir Path library) {
        TestCompiler.create()
                .source("com.acme.shop.lib.Precompiled", "package com.acme.shop.lib; public class Precompiled { }")
                .compile(library);

        TestCompiler.Result result = shop().classpath(library).compile(output);

        assertFalse(result.index().orElseThrow().contains("Precompiled"), result.index().orElseThrow());
    }

    @Test
    void independentCompilationsProduceIndependentIndexes(@TempDir Path other) {
        TestCompiler.Result shop = shop().compile(output);
        TestCompiler.Result blog = TestCompiler.create()
                .source("org.blog.BlogApp", "package org.blog; public class BlogApp { }")
                .application("org.blog.BlogApp")
                .compile(other);

        assertFalse(shop.index().orElseThrow().contains("org.blog"));
        assertEquals("dhole-metadata 1\n\ncomponent org.blog.BlogApp\nconstructor\nsource org/blog/BlogApp.java:1\n",
                blog.index().orElseThrow());
    }

    @Test
    void metadataContainsNoLiteralValuesOrAbsolutePaths() {
        TestCompiler.Result result = app().source("com.acme.shop.Secrets", """
                package com.acme.shop;
                public class Secrets {
                    static final String JWT_SECRET = "super-secret-value";
                    private final String password = "hunter2";
                }
                """).compile(output);

        String index = result.index().orElseThrow();
        assertFalse(index.contains("super-secret-value"), index);
        assertFalse(index.contains("hunter2"), index);
        assertFalse(index.contains(output.toString()), index);
        assertFalse(index.contains(":\\") || index.contains("string:"), index);
    }

    @Test
    void otherProcessorsKeepWorkingAndTheirGeneratedTypesAreSeen() {
        TestCompiler.Result result = shop().compile(output, new GeneratingProcessor());

        assertTrue(result.success(), result.diagnostics().toString());
        assertTrue(result.index().orElseThrow().contains("component com.acme.shop.generated.Generated\nconstructor\n"),
                result.index().orElseThrow());
    }

    /**
     * Another processor that generates a source type in the application root in its first round.
     */
    static final class GeneratingProcessor extends AbstractProcessor {

        private boolean generated;

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of("*");
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
            if (!generated) {
                generated = true;
                try (var writer = processingEnv.getFiler()
                        .createSourceFile("com.acme.shop.generated.Generated").openWriter()) {
                    writer.write("package com.acme.shop.generated; public class Generated { }");
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
            return false;
        }
    }

    private static final String APP = """
            package com.acme.shop;

            public final class App {
                private App() { }
                public static void main(String[] args) { }
            }
            """;

    private static final String USER_REPOSITORY = """
            package com.acme.shop.users;

            public class UserRepository {
            }
            """;

    private static final String USER_SERVICE = """
            package com.acme.shop.users;

            public class UserService {
                private final UserRepository users;

                public UserService(UserRepository users) {
                    this.users = users;
                }
            }
            """;

    private static TestCompiler shop() {
        return TestCompiler.create()
                .source("com.acme.shop.App", APP)
                .source("com.acme.shop.users.UserRepository", USER_REPOSITORY)
                .source("com.acme.shop.users.UserService", USER_SERVICE)
                .application("com.acme.shop.App");
    }

    private static TestCompiler app() {
        return TestCompiler.create()
                .source("com.acme.shop.App", "package com.acme.shop; public class App { }")
                .application("com.acme.shop.App");
    }
}
