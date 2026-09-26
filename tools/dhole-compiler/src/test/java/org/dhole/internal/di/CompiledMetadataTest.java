package org.dhole.internal.di;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;

import org.dhole.di.AmbiguousDependencyException;
import org.dhole.di.CircularDependencyException;
import org.dhole.di.DependencyException;
import org.dhole.internal.compiler.TestCompiler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End to end: fixture applications compiled with the metadata processor, their generated
 * {@code components.idx} read by the dependency container (roadmap M4 fixtures: valid project,
 * missing dependency, ambiguous dependency, multiple constructors).
 *
 * <p>Lives in the container's package to reach its internal API; production code of dhole-di does
 * not depend on the compiler.
 */
class CompiledMetadataTest {

    @TempDir
    Path output;

    private URLClassLoader loader;

    @AfterEach
    void closeLoader() throws IOException {
        if (loader != null) {
            loader.close();
        }
    }

    @Test
    void validProjectResolvesFromGeneratedMetadata() throws Exception {
        DependencyContainer container = container(compile(PAYMENT_GATEWAY, STRIPE, ORDER_SERVICE, USER_REPOSITORY,
                USER_SERVICE, USER_CONTROLLER));

        Object orders = container.resolve(type("com.acme.shop.orders.OrderService"));
        Object controller = container.resolve(type("com.acme.shop.users.UserController"));

        assertEquals("com.acme.shop.payments.StripeGateway",
                orders.getClass().getMethod("gatewayType").invoke(orders));
        assertEquals("UserController\n└── UserService\n    └── UserRepository",
                container.graph(type("com.acme.shop.users.UserController")).render());
        assertEquals(controller, container.resolve(type("com.acme.shop.users.UserController")));
    }

    @Test
    void missingDependencyIsReportedWithCodeAndSourceLocation() throws Exception {
        DependencyContainer container = container(compile(PAYMENT_GATEWAY, ORDER_SERVICE));

        DependencyException failure = assertThrows(DependencyException.class,
                () -> container.resolve(type("com.acme.shop.orders.OrderService")));

        assertEquals("Dependency Error DHOLE-DI-001\n\nNo provider found for PaymentGateway.\n\n"
                + "Dependency path:\nOrderService\n  -> PaymentGateway\n\n"
                + "Required at:\ncom/acme/shop/orders/OrderService.java:8", failure.getMessage());
    }

    @Test
    void ambiguousDependencyIsReportedWithCodeAndSourceLocation() throws Exception {
        DependencyContainer container = container(compile(PAYMENT_GATEWAY, STRIPE, PAYPAL, ORDER_SERVICE));

        AmbiguousDependencyException failure = assertThrows(AmbiguousDependencyException.class,
                () -> container.resolve(type("com.acme.shop.orders.OrderService")));

        assertEquals("Dependency Error DHOLE-DI-002\n\nMultiple providers found for PaymentGateway:\n\n"
                + "- PaypalGateway\n- StripeGateway\n\n"
                + "Dependency path:\nOrderService\n  -> PaymentGateway\n\n"
                + "Required at:\ncom/acme/shop/orders/OrderService.java:8\n\nDeclare an explicit binding.",
                failure.getMessage());
    }

    @Test
    void multipleConstructorsAreReportedWithSourceLocation() throws Exception {
        DependencyContainer container = container(compile(USER_REPOSITORY, new String[] {
                "com.acme.shop.users.UserService", """
                package com.acme.shop.users;

                public class UserService {
                    public UserService() { }
                    public UserService(UserRepository users) { }
                }
                """}));

        DependencyException failure = assertThrows(DependencyException.class,
                () -> container.resolve(type("com.acme.shop.users.UserService")));

        assertEquals("Dependency Error\n\nCannot create UserService.\n\nProblem:\n"
                + "UserService has 2 public constructors.\nDeclare exactly one constructor or register a factory."
                + "\n\nLocation:\ncom/acme/shop/users/UserService.java:3", failure.getMessage());
    }

    @Test
    void circularDependencyIsReportedWithCode() throws Exception {
        DependencyContainer container = container(compile(
                new String[] {"com.acme.shop.A", "package com.acme.shop; public class A { public A(B b) { } }"},
                new String[] {"com.acme.shop.B", "package com.acme.shop; public class B { public B(A a) { } }"}));

        CircularDependencyException failure = assertThrows(CircularDependencyException.class,
                () -> container.resolve(type("com.acme.shop.A")));

        assertEquals("Circular Dependency DHOLE-DI-003\n\nA\n  -> B\n      -> A", failure.getMessage());
    }

    @Test
    void generatedMetadataAndReflectionDefineComponentsIdentically() throws Exception {
        ComponentMetadata metadata = compile(PAYMENT_GATEWAY, STRIPE, ORDER_SERVICE, USER_REPOSITORY, USER_SERVICE,
                USER_CONTROLLER,
                new String[] {"com.acme.shop.Multiple", """
                        package com.acme.shop;
                        public class Multiple { public Multiple() { } public Multiple(String s) { } }
                        """},
                new String[] {"com.acme.shop.Hidden", "package com.acme.shop; class Hidden { }"},
                new String[] {"com.acme.shop.Generic", """
                        package com.acme.shop;
                        public class Generic { public Generic(java.util.List<String> values) { } }
                        """});
        ComponentRegistry indexed = ContainerBuilder.create().metadata(metadata).registry();
        ComponentRegistry reflective = ContainerBuilder.create().registry();
        List<String> types = List.of("com.acme.shop.payments.StripeGateway", "com.acme.shop.orders.OrderService",
                "com.acme.shop.users.UserRepository", "com.acme.shop.users.UserService",
                "com.acme.shop.users.UserController", "com.acme.shop.Multiple", "com.acme.shop.Hidden",
                "com.acme.shop.Generic");

        for (String name : types) {
            Class<?> type = type(name);
            String fromIndex = describe(indexed, type);
            String fromReflection = describe(reflective, type);
            assertTrue(fromIndex.startsWith(fromReflection), name + ":\n" + fromIndex + "\n---\n" + fromReflection);
        }
    }

    private static String describe(ComponentRegistry registry, Class<?> type) {
        try {
            ComponentDefinition definition = registry.definition(type, List.of(type));
            return definition.description() + " " + definition.dependencies() + " " + definition.scope();
        } catch (DependencyException e) {
            return e.getMessage();
        }
    }

    private static final String[] PAYMENT_GATEWAY = {"com.acme.shop.payments.PaymentGateway", """
            package com.acme.shop.payments;

            public interface PaymentGateway {
            }
            """};

    private static final String[] STRIPE = {"com.acme.shop.payments.StripeGateway", """
            package com.acme.shop.payments;

            public class StripeGateway implements PaymentGateway {
            }
            """};

    private static final String[] PAYPAL = {"com.acme.shop.payments.PaypalGateway", """
            package com.acme.shop.payments;

            public class PaypalGateway implements PaymentGateway {
            }
            """};

    private static final String[] ORDER_SERVICE = {"com.acme.shop.orders.OrderService", """
            package com.acme.shop.orders;

            import com.acme.shop.payments.PaymentGateway;

            public class OrderService {
                private final PaymentGateway payments;

                public OrderService(PaymentGateway payments) {
                    this.payments = payments;
                }

                public String gatewayType() {
                    return payments.getClass().getName();
                }
            }
            """};

    private static final String[] USER_REPOSITORY = {"com.acme.shop.users.UserRepository", """
            package com.acme.shop.users;

            public class UserRepository {
            }
            """};

    private static final String[] USER_SERVICE = {"com.acme.shop.users.UserService", """
            package com.acme.shop.users;

            public class UserService {
                public UserService(UserRepository users) {
                }
            }
            """};

    private static final String[] USER_CONTROLLER = {"com.acme.shop.users.UserController", """
            package com.acme.shop.users;

            public class UserController {
                public UserController(UserService users) {
                }
            }
            """};

    private ComponentMetadata compile(String[]... sources) throws IOException {
        TestCompiler compiler = TestCompiler.create()
                .source("com.acme.shop.App", "package com.acme.shop; public final class App { private App() { } }")
                .application("com.acme.shop.App");
        for (String[] source : sources) {
            compiler.source(source[0], source[1]);
        }
        TestCompiler.Result result = compiler.compile(output);
        assertTrue(result.success(), result.diagnostics().toString());
        loader = new URLClassLoader(new URL[] {output.toUri().toURL()}, getClass().getClassLoader());
        return ComponentMetadata.load(loader);
    }

    private static DependencyContainer container(ComponentMetadata metadata) {
        return ContainerBuilder.create().metadata(metadata).build();
    }

    private Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }
}
