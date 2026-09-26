package org.dhole.internal.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ValidationAnalyzerTest {

    @TempDir
    Path output;

    private static final String APP = "package com.acme.shop; public final class App { private App() { } }";

    @Test
    void singleFieldOfARecord() {
        TestCompiler.Result result = compile("CreateUser", """
                package com.acme.shop;

                import org.dhole.validation.Rules;
                import org.dhole.validation.Validatable;

                public record CreateUser(String name) implements Validatable {

                    public static Rules<CreateUser> rules() {
                        return Rules.forType(CreateUser.class).field(CreateUser::name).required().notBlank();
                    }
                }
                """);

        assertTrue(result.success(), result.diagnostics().toString());
        assertEquals(String.join("\n",
                "dhole-validation 1",
                "",
                "type com.acme.shop.CreateUser",
                "field name java.lang.String",
                "source com/acme/shop/CreateUser.java:6",
                ""), index(result));
    }

    @Test
    void multipleFieldsKeepDeclarationOrderIncludingNestedAndChecks() {
        TestCompiler.Result result = base()
                .source("com.acme.shop.Item", """
                        package com.acme.shop;

                        import org.dhole.validation.Rules;
                        import org.dhole.validation.Validatable;

                        public record Item(int quantity) implements Validatable {
                            public static Rules<Item> rules() {
                                return Rules.forType(Item.class).field(Item::quantity).positive();
                            }
                        }
                        """)
                .source("com.acme.shop.Order", """
                        package com.acme.shop;

                        import java.util.List;
                        import org.dhole.validation.Rules;
                        import org.dhole.validation.Validatable;
                        import org.dhole.validation.Validation;

                        public record Order(String reference, List<Item> items, Item first, long total)
                                implements Validatable {
                            public static Rules<Order> rules() {
                                return Rules.forType(Order.class)
                                        .field(Order::total).min(1).max(100)
                                        .field(Order::reference).required().minLength(3).email()
                                        .check(order -> Validation.success())
                                        .field(Order::items).notEmpty().eachNested()
                                        .field(Order::first).nested();
                            }
                        }
                        """)
                .compile(output);

        assertTrue(result.success(), result.diagnostics().toString());
        assertTrue(index(result).contains("""
                type com.acme.shop.Order
                field total long
                field reference java.lang.String
                field items java.util.List<com.acme.shop.Item>
                field first com.acme.shop.Item
                """), index(result));
        assertTrue(index(result).indexOf("type com.acme.shop.Item\n") < index(result).indexOf("type com.acme.shop.Order\n"));
    }

    @Test
    void getterAccessorsAreNamedAsProperties() {
        TestCompiler.Result result = compile("Account", """
                package com.acme.shop;

                import org.dhole.validation.Rules;
                import org.dhole.validation.Validatable;

                public final class Account implements Validatable {
                    public String getEmail() { return "a@b.co"; }
                    public Boolean isActive() { return true; }
                    public String code() { return "x"; }

                    public static Rules<Account> rules() {
                        return Rules.forType(Account.class)
                                .field(Account::getEmail).email()
                                .field(Account::isActive).required()
                                .field(Account::code).notBlank();
                    }
                }
                """);

        assertTrue(result.success(), result.diagnostics().toString());
        assertTrue(index(result).contains("field email java.lang.String\nfield active java.lang.Boolean\n"
                + "field code java.lang.String\n"), index(result));
    }

    @Test
    void lambdaInsteadOfMethodReferenceIsRejected() {
        TestCompiler.Result result = compile("CreateUser", """
                package com.acme.shop;

                import org.dhole.validation.Rules;
                import org.dhole.validation.Validatable;

                public record CreateUser(String name) implements Validatable {

                    public static Rules<CreateUser> rules() {
                        return Rules.forType(CreateUser.class).field(user -> user.name()).required();
                    }
                }
                """);

        assertFailed(result, "DHOLE-VAL-002", "CreateUser.java:9");
    }

    @Test
    void accessorOfAnotherTypeIsRejected() {
        TestCompiler.Result result = compile("CreateUser", """
                package com.acme.shop;

                import org.dhole.validation.Rules;
                import org.dhole.validation.Validatable;

                public record CreateUser(String name) implements Validatable {

                    public static Rules<CreateUser> rules() {
                        return Rules.forType(CreateUser.class).field(Object::toString).required();
                    }
                }
                """);

        assertFailed(result, "DHOLE-VAL-002", "CreateUser.java:9");
    }

    @Test
    void conditionalAndComputedRulesAreRejected() {
        TestCompiler.Result conditional = compile("CreateUser", """
                package com.acme.shop;

                import org.dhole.validation.Rules;
                import org.dhole.validation.Validatable;

                public record CreateUser(String name) implements Validatable {

                    static final boolean STRICT = Boolean.getBoolean("strict");

                    public static Rules<CreateUser> rules() {
                        Rules<CreateUser> rules = Rules.forType(CreateUser.class);
                        if (STRICT) {
                            return rules.field(CreateUser::name).required();
                        }
                        return rules;
                    }
                }
                """);
        assertFailed(conditional, "DHOLE-VAL-001", "CreateUser.java:10");

        TestCompiler.Result ternary = compile("Other", """
                package com.acme.shop;

                import org.dhole.validation.Rules;
                import org.dhole.validation.Validatable;

                public record Other(String name) implements Validatable {

                    public static Rules<Other> rules() {
                        return Boolean.getBoolean("strict")
                                ? Rules.forType(Other.class).field(Other::name).required()
                                : Rules.forType(Other.class);
                    }
                }
                """);
        assertFailed(ternary, "DHOLE-VAL-001", "Other.java:9");
    }

    @Test
    void validatableWithoutRulesIsRejected() {
        TestCompiler.Result result = compile("CreateUser", """
                package com.acme.shop;

                public record CreateUser(String name) implements org.dhole.validation.Validatable {
                }
                """);

        assertFailed(result, "DHOLE-VAL-001", "CreateUser.java:3");
    }

    @Test
    void wrongTypeRulesNestedAndDuplicatesAreRejected() {
        TestCompiler.Result result = compile("CreateUser", """
                package com.acme.shop;

                import java.util.List;
                import org.dhole.validation.Rules;
                import org.dhole.validation.Validatable;

                public record CreateUser(String name, int age, String nick, List<String> tags) implements Validatable {

                    public static Rules<CreateUser> rules() {
                        return Rules.forType(CreateUser.class)
                                .field(CreateUser::age).email()
                                .field(CreateUser::name).positive()
                                .field(CreateUser::nick).nested()
                                .field(CreateUser::tags).eachNested()
                                .field(CreateUser::name).required();
                    }
                }
                """);

        assertFalse(result.success());
        String diagnostics = String.join("\n", result.diagnostics());
        assertTrue(diagnostics.contains("DHOLE-VAL-003") && diagnostics.contains("email() needs a text field, not int")
                && diagnostics.contains("CreateUser.java:11"), diagnostics);
        assertTrue(diagnostics.contains("positive() needs a number field") && diagnostics.contains("CreateUser.java:12"),
                diagnostics);
        assertTrue(diagnostics.contains("DHOLE-VAL-004") && diagnostics.contains("CreateUser.java:13")
                && diagnostics.contains("CreateUser.java:14"), diagnostics);
        assertTrue(diagnostics.contains("DHOLE-VAL-005") && diagnostics.contains("CreateUser.java:15"), diagnostics);
    }

    @Test
    void noValidatableTypesStillWritesAnEmptyIndex() {
        TestCompiler.Result result = base().compile(output);

        assertTrue(result.success(), result.diagnostics().toString());
        assertEquals("dhole-validation 1\n", index(result));
    }

    @Test
    void validationIndexIsByteForByteDeterministic(@TempDir Path first, @TempDir Path second) throws IOException {
        String a = """
                package com.acme.shop;
                public record A(String x) implements org.dhole.validation.Validatable {
                    public static org.dhole.validation.Rules<A> rules() {
                        return org.dhole.validation.Rules.forType(A.class).field(A::x).required();
                    }
                }
                """;
        String b = a.replace("A(", "B(").replace("<A>", "<B>").replace("A.class", "B.class").replace("A::", "B::");
        base().source("com.acme.shop.A", a).source("com.acme.shop.B", b).compile(first);
        base().source("com.acme.shop.B", b).source("com.acme.shop.A", a).compile(second);

        assertEquals(-1L, Files.mismatch(first.resolve(ValidationRecord.LOCATION), second.resolve(ValidationRecord.LOCATION)));
    }

    @Test
    void generatedIndexContainsNoSerializedLambdaOrInputValues() {
        TestCompiler.Result result = compile("CreateUser", """
                package com.acme.shop;

                import org.dhole.validation.Rules;
                import org.dhole.validation.Validatable;

                public record CreateUser(String name) implements Validatable {

                    public static Rules<CreateUser> rules() {
                        return Rules.forType(CreateUser.class).field(CreateUser::name).required().minLength(2);
                    }
                }
                """);

        assertTrue(result.success(), result.diagnostics().toString());
        assertFalse(index(result).contains("lambda") || index(result).contains("Serialized") || index(result).contains("minLength"),
                index(result));
    }

    private static void assertFailed(TestCompiler.Result result, String code, String location) {
        assertFalse(result.success());
        String diagnostics = String.join("\n", result.diagnostics());
        assertTrue(diagnostics.contains(code) && diagnostics.contains(location), diagnostics);
    }

    private TestCompiler base() {
        return TestCompiler.create().source("com.acme.shop.App", APP).application("com.acme.shop.App");
    }

    private TestCompiler.Result compile(String name, String source) {
        return base().source("com.acme.shop." + name, source).compile(output);
    }

    private String index(TestCompiler.Result result) {
        try {
            return Files.readString(result.output().resolve(ValidationRecord.LOCATION), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
