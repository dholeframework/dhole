package org.dhole.internal.di;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpClient;
import java.time.Clock;
import java.util.List;

import org.dhole.di.AmbiguousDependencyException;
import org.dhole.di.CircularDependencyException;
import org.dhole.di.DependencyException;
import org.dhole.internal.di.Fixtures.CycleA;
import org.dhole.internal.di.Fixtures.CycleB;
import org.dhole.internal.di.Fixtures.CycleC;
import org.dhole.internal.di.Fixtures.LoopingGateway;
import org.dhole.internal.di.Fixtures.Mail;
import org.dhole.internal.di.Fixtures.NeedsMissing;
import org.dhole.internal.di.Fixtures.OrderService;
import org.dhole.internal.di.Fixtures.PaymentGateway;
import org.dhole.internal.di.Fixtures.PaypalPaymentGateway;
import org.dhole.internal.di.Fixtures.SelfDependent;
import org.dhole.internal.di.Fixtures.StripePaymentGateway;
import org.dhole.internal.di.Fixtures.TwoConstructors;
import org.dhole.internal.di.Fixtures.UserController;
import org.dhole.internal.di.Fixtures.UserRepository;
import org.dhole.internal.di.Fixtures.UserService;
import org.junit.jupiter.api.Test;

class DependencyGraphTest {

    @Test
    void nestedDependenciesFormAnInspectableGraph() {
        DependencyGraph graph = graph(ContainerBuilder.create(), UserController.class);

        DependencyNode controller = graph.roots().get(0);
        assertEquals(UserController.class, controller.requestedType());
        assertEquals(List.of(UserService.class, Mail.class),
                controller.dependencies().stream().map(DependencyNode::requestedType).toList());
        DependencyNode service = controller.dependencies().get(0);
        assertEquals(List.of(UserRepository.class),
                service.dependencies().stream().map(DependencyNode::requestedType).toList());
        assertSame(service, graph.node(UserService.class).orElseThrow());
        assertEquals("UserService(UserRepository)", service.definition().description());
    }

    @Test
    void graphRendersAsATree() {
        DependencyGraph graph = graph(ContainerBuilder.create(), UserController.class);

        assertEquals(String.join("\n",
                "UserController",
                "├── UserService",
                "│   └── UserRepository",
                "└── Mail"), graph.render());
    }

    @Test
    void renderingShowsBoundImplementationsFactoriesAndInstances() {
        DependencyGraph graph = graph(ContainerBuilder.create()
                        .bind(PaymentGateway.class).to(StripePaymentGateway.class)
                        .provide(HttpClient.class, context -> HttpClient.newHttpClient())
                        .bind(Clock.class).toInstance(Clock.systemUTC()),
                OrderService.class, HttpClient.class, Clock.class);

        assertEquals(String.join("\n",
                "OrderService",
                "└── PaymentGateway (StripePaymentGateway)",
                "HttpClient (factory)",
                "Clock (instance)"), graph.render());
    }

    @Test
    void sharedDependenciesAreOneNode() {
        DependencyGraph graph = graph(ContainerBuilder.create(), UserController.class, UserService.class);

        assertSame(graph.roots().get(0).dependencies().get(0), graph.roots().get(1));
    }

    @Test
    void dependencyOrderPlacesDependenciesFirstDeterministically() {
        DependencyGraph graph = graph(ContainerBuilder.create(), UserController.class);

        assertEquals(List.of(UserRepository.class, UserService.class, Mail.class, UserController.class),
                graph.dependencyOrder().stream().map(DependencyNode::requestedType).toList());
    }

    @Test
    void cycleFailsWithTheCyclePath() {
        CircularDependencyException failure = assertThrows(CircularDependencyException.class,
                () -> graph(ContainerBuilder.create(), CycleA.class));

        assertEquals("Circular Dependency DHOLE-DI-003\n\nCycleA\n  -> CycleB\n      -> CycleC\n          -> CycleA",
                failure.getMessage());
    }

    @Test
    void cycleIsReportedFromWhereverItIsEntered() {
        CircularDependencyException failure = assertThrows(CircularDependencyException.class,
                () -> graph(ContainerBuilder.create(), CycleB.class));

        assertEquals("Circular Dependency DHOLE-DI-003\n\nCycleB\n  -> CycleC\n      -> CycleA\n          -> CycleB",
                failure.getMessage());
    }

    @Test
    void selfDependencyIsACycle() {
        assertEquals("Circular Dependency DHOLE-DI-003\n\nSelfDependent\n  -> SelfDependent",
                assertThrows(CircularDependencyException.class,
                        () -> graph(ContainerBuilder.create(), SelfDependent.class)).getMessage());
    }

    @Test
    void cycleThroughABindingIsDetected() {
        ContainerBuilder builder = ContainerBuilder.create().bind(PaymentGateway.class).to(LoopingGateway.class);

        CircularDependencyException failure = assertThrows(CircularDependencyException.class,
                () -> graph(builder, OrderService.class));

        assertEquals("Circular Dependency DHOLE-DI-003\n\nOrderService\n  -> PaymentGateway\n      -> OrderService",
                failure.getMessage());
    }

    @Test
    void missingNestedDependencyShowsThePath() {
        DependencyException failure = assertThrows(DependencyException.class,
                () -> graph(ContainerBuilder.create(), NeedsMissing.class));

        assertEquals("Dependency Error DHOLE-DI-001\n\nNo provider found for AbstractStorage.\n\n"
                + "Dependency path:\nNeedsMissing\n  -> AbstractStorage", failure.getMessage());
    }

    @Test
    void ambiguousNestedDependencyShowsCandidatesAndPath() {
        ContainerBuilder builder = ContainerBuilder.create()
                .component(StripePaymentGateway.class)
                .component(PaypalPaymentGateway.class);

        AmbiguousDependencyException failure = assertThrows(AmbiguousDependencyException.class,
                () -> graph(builder, OrderService.class));

        assertEquals("Dependency Error DHOLE-DI-002\n\nMultiple providers found for PaymentGateway:\n\n"
                + "- PaypalPaymentGateway\n- StripePaymentGateway\n\n"
                + "Dependency path:\nOrderService\n  -> PaymentGateway\n\nDeclare an explicit binding.",
                failure.getMessage());
    }

    @Test
    void unusableNestedConstructorShowsThePath() {
        DependencyException failure = assertThrows(DependencyException.class,
                () -> graph(ContainerBuilder.create(), NeedsTwoConstructors.class));

        assertTrue(failure.getMessage().contains("TwoConstructors has 2 public constructors."), failure.getMessage());
        assertTrue(failure.getMessage().endsWith("Dependency path:\nNeedsTwoConstructors\n  -> TwoConstructors"),
                failure.getMessage());
    }

    @Test
    void graphExposesNoMutableCollections() {
        DependencyGraph graph = graph(ContainerBuilder.create(), UserController.class);

        assertThrows(UnsupportedOperationException.class, () -> graph.roots().clear());
        assertThrows(UnsupportedOperationException.class, () -> graph.roots().get(0).dependencies().clear());
        assertThrows(UnsupportedOperationException.class, () -> graph.dependencyOrder().clear());
    }

    public static final class NeedsTwoConstructors {

        public NeedsTwoConstructors(TwoConstructors ambiguous) {
        }
    }

    private static DependencyGraph graph(ContainerBuilder builder, Class<?>... roots) {
        return new DependencyGraphBuilder(builder.registry()).build(List.of(roots));
    }
}
