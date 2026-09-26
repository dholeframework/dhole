package org.dhole.internal.di;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.dhole.di.CircularDependencyException;
import org.dhole.di.DependencyException;
import org.dhole.internal.di.Fixtures.CycleA;
import org.dhole.internal.di.Fixtures.Mail;
import org.dhole.internal.di.Fixtures.OrderService;
import org.dhole.internal.di.Fixtures.PaymentGateway;
import org.dhole.internal.di.Fixtures.PaypalPaymentGateway;
import org.dhole.internal.di.Fixtures.StripePaymentGateway;
import org.dhole.internal.di.Fixtures.UserController;
import org.dhole.internal.di.Fixtures.UserRepository;
import org.dhole.internal.di.Fixtures.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DependencyContainerTest {

    @BeforeEach
    void resetCounters() {
        Counted.created.set(0);
        SlowSingleton.created.set(0);
    }

    // Constructor injection

    @Test
    void simpleDependencyIsInjectedThroughTheConstructor() {
        DependencyContainer container = ContainerBuilder.create().build();

        UserService service = container.resolve(UserService.class);

        assertInstanceOf(UserRepository.class, service.users);
    }

    @Test
    void nestedDependenciesAreResolvedRecursively() {
        DependencyContainer container = ContainerBuilder.create().build();

        UserController controller = container.resolve(UserController.class);

        assertSame(container.resolve(UserService.class), controller.users);
        assertSame(container.resolve(UserRepository.class), controller.users.users);
        assertSame(container.resolve(Mail.class), controller.mail);
    }

    @Test
    void explicitBindingProvidesTheBoundImplementation() {
        DependencyContainer container = ContainerBuilder.create()
                .component(StripePaymentGateway.class)
                .component(PaypalPaymentGateway.class)
                .bind(PaymentGateway.class).to(PaypalPaymentGateway.class)
                .build();

        OrderService orders = container.resolve(OrderService.class);

        assertInstanceOf(PaypalPaymentGateway.class, orders.payments);
        assertSame(container.resolve(PaypalPaymentGateway.class), orders.payments);
    }

    @Test
    void inferredInterfaceImplementationIsUsed() {
        DependencyContainer container = ContainerBuilder.create().component(StripePaymentGateway.class).build();

        assertInstanceOf(StripePaymentGateway.class, container.resolve(OrderService.class).payments);
    }

    @Test
    void instanceBindingProvidesTheInstance() {
        UserRepository repository = new UserRepository();
        DependencyContainer container = ContainerBuilder.create()
                .bind(UserRepository.class).toInstance(repository)
                .build();

        assertSame(repository, container.resolve(UserService.class).users);
    }

    // Factories

    @Test
    void factoryBindingCreatesTheComponentWithResolvedDependencies() {
        DependencyContainer container = ContainerBuilder.create()
                .provide(UserService.class, context -> new UserService(context.resolve(UserRepository.class)))
                .provide(HttpClient.class, context -> HttpClient.newHttpClient())
                .build();

        assertSame(container.resolve(UserRepository.class), container.resolve(UserService.class).users);
        assertSame(container.resolve(HttpClient.class), container.resolve(HttpClient.class));
    }

    @Test
    void factoryReturningNullFails() {
        DependencyException failure = assertThrows(DependencyException.class,
                () -> ContainerBuilder.create().provide(UserRepository.class, context -> null).build());

        assertTrue(failure.getMessage().endsWith("Problem:\nThe factory returned null."), failure.getMessage());
    }

    @Test
    void factoryExceptionIsWrappedWithItsCause() {
        IllegalStateException cause = new IllegalStateException("boom");

        DependencyException failure = assertThrows(DependencyException.class,
                () -> ContainerBuilder.create().provide(UserRepository.class, context -> {
                    throw cause;
                }).build());

        assertEquals("Dependency Error\n\nCould not construct UserRepository.\n\nProvider:\nfactory for UserRepository",
                failure.getMessage());
        assertSame(cause, failure.getCause());
    }

    @Test
    void factoryCycleIsDetectedAtCreation() {
        CircularDependencyException failure = assertThrows(CircularDependencyException.class,
                () -> ContainerBuilder.create()
                        .provide(UserRepository.class, context -> {
                            context.resolve(UserService.class);
                            return new UserRepository();
                        })
                        .build());

        assertEquals("Circular Dependency\n\nUserRepository\n  -> UserService\n      -> UserRepository",
                failure.getMessage());
    }

    // Constructor failures and fail-early validation

    @Test
    void constructorExceptionIsWrappedWithItsCauseAndPath() {
        DependencyContainer container = ContainerBuilder.create().build();

        DependencyException failure = assertThrows(DependencyException.class,
                () -> container.resolve(NeedsFailing.class));

        assertEquals("Dependency Error\n\nCould not construct Failing.\n\nConstructor:\nFailing()\n\n"
                + "Dependency path:\nNeedsFailing\n  -> Failing", failure.getMessage());
        assertInstanceOf(IllegalStateException.class, failure.getCause());
    }

    @Test
    void graphProblemsFailBeforeAnyInstanceIsCreated() {
        DependencyContainer container = ContainerBuilder.create().build();

        assertThrows(DependencyException.class, () -> container.resolve(CountedThenMissing.class));

        assertEquals(0, Counted.created.get());
    }

    @Test
    void invalidRegistrationsFailWhenTheContainerIsBuilt() {
        assertThrows(CircularDependencyException.class, () -> ContainerBuilder.create().component(CycleA.class).build());
        assertThrows(DependencyException.class,
                () -> ContainerBuilder.create().component(Counted.class).component(CountedThenMissing.class).build());
        assertEquals(0, Counted.created.get());
    }

    @Test
    void containerExposesItsGraphs() {
        DependencyContainer container = ContainerBuilder.create().component(UserController.class).build();

        assertEquals("UserController\n├── UserService\n│   └── UserRepository\n└── Mail",
                container.registeredGraph().render());
        assertEquals("UserService\n└── UserRepository", container.graph(UserService.class).render());
    }

    // Scopes

    @Test
    void singletonIsReused() {
        DependencyContainer container = ContainerBuilder.create().build();

        assertSame(container.resolve(Counted.class), container.resolve(Counted.class));
        assertEquals(1, Counted.created.get());
    }

    @Test
    void singletonIsSharedAcrossAbstractionAndImplementation() {
        DependencyContainer container = ContainerBuilder.create()
                .bind(PaymentGateway.class).to(StripePaymentGateway.class)
                .build();

        assertSame(container.resolve(PaymentGateway.class), container.resolve(StripePaymentGateway.class));
    }

    @Test
    void registeredSingletonsAreCreatedWhenTheContainerStarts() {
        ContainerBuilder.create().component(Counted.class).build();

        assertEquals(1, Counted.created.get());
    }

    @Test
    void prototypeIsRecreatedOnEveryResolution() {
        DependencyContainer container = ContainerBuilder.create()
                .component(Counted.class, ComponentScope.PROTOTYPE)
                .build();

        Counted first = container.resolve(Counted.class);
        Counted second = container.resolve(Counted.class);

        assertNotSame(first, second);
        assertEquals(2, Counted.created.get());
    }

    @Test
    void prototypeInstancesAreIndependentButShareSingletonDependencies() {
        DependencyContainer container = ContainerBuilder.create()
                .component(UserService.class, ComponentScope.PROTOTYPE)
                .build();

        UserService first = container.resolve(UserService.class);
        UserService second = container.resolve(UserService.class);

        assertNotSame(first, second);
        assertSame(first.users, second.users);
    }

    @Test
    void prototypeFactoryRunsOnEveryResolution() {
        AtomicInteger calls = new AtomicInteger();
        DependencyContainer container = ContainerBuilder.create()
                .provide(UserRepository.class, ComponentScope.PROTOTYPE, context -> {
                    calls.incrementAndGet();
                    return new UserRepository();
                })
                .build();

        assertNotSame(container.resolve(UserRepository.class), container.resolve(UserRepository.class));
        assertEquals(2, calls.get());
    }

    @Test
    void singletonKeepsTheInstanceOfAPrototypeDependencyItReceived() {
        DependencyContainer container = ContainerBuilder.create()
                .component(UserRepository.class, ComponentScope.PROTOTYPE)
                .build();

        UserService service = container.resolve(UserService.class);

        assertSame(service.users, container.resolve(UserService.class).users);
        assertNotSame(service.users, container.resolve(UserRepository.class));
    }

    @Test
    void independentContainersDoNotShareSingletons() {
        DependencyContainer first = ContainerBuilder.create().build();
        DependencyContainer second = ContainerBuilder.create().build();

        assertNotSame(first.resolve(UserService.class), second.resolve(UserService.class));
    }

    @Test
    void concurrentResolutionCreatesExactlyOneSingleton() throws Exception {
        DependencyContainer container = ContainerBuilder.create().build();
        int threads = 16;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<SlowSingleton>> results = new ArrayList<>();
            for (int index = 0; index < threads; index++) {
                Callable<SlowSingleton> task = () -> {
                    start.await();
                    return container.resolve(SlowSingleton.class);
                };
                results.add(executor.submit(task));
            }
            start.countDown();
            SlowSingleton expected = results.get(0).get();
            for (Future<SlowSingleton> result : results) {
                assertSame(expected, result.get());
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, SlowSingleton.created.get());
    }

    public static final class Counted {

        static final AtomicInteger created = new AtomicInteger();

        public Counted() {
            created.incrementAndGet();
        }
    }

    public static final class CountedThenMissing {

        public CountedThenMissing(Counted counted, PaymentGateway missing) {
        }
    }

    public static final class Failing {

        public Failing() {
            throw new IllegalStateException("constructor failure");
        }
    }

    public static final class NeedsFailing {

        public NeedsFailing(Failing failing) {
        }
    }

    public static final class SlowSingleton {

        static final AtomicInteger created = new AtomicInteger();

        public SlowSingleton() throws InterruptedException {
            created.incrementAndGet();
            Thread.sleep(20);
        }
    }
}
