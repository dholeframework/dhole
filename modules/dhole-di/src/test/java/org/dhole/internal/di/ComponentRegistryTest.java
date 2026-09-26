package org.dhole.internal.di;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.util.List;

import org.dhole.di.AmbiguousDependencyException;
import org.dhole.di.DependencyException;
import org.dhole.internal.di.Fixtures.AbstractStorage;
import org.dhole.internal.di.Fixtures.InnerClass;
import org.dhole.internal.di.Fixtures.NeedsList;
import org.dhole.internal.di.Fixtures.PackagePrivateClass;
import org.dhole.internal.di.Fixtures.PaymentGateway;
import org.dhole.internal.di.Fixtures.PaypalPaymentGateway;
import org.dhole.internal.di.Fixtures.PrivateConstructorOnly;
import org.dhole.internal.di.Fixtures.PublicAndPrivateConstructors;
import org.dhole.internal.di.Fixtures.StripePaymentGateway;
import org.dhole.internal.di.Fixtures.TwoConstructors;
import org.dhole.internal.di.Fixtures.UserRepository;
import org.dhole.internal.di.Fixtures.UserService;
import org.junit.jupiter.api.Test;

class ComponentRegistryTest {

    @Test
    void concreteClassIsDefinedByItsConstructor() {
        ComponentDefinition definition = definition(ContainerBuilder.create(), UserService.class);

        assertEquals(UserService.class, definition.type());
        assertEquals(ComponentDefinition.Kind.CONSTRUCTOR, definition.kind());
        assertEquals(ComponentScope.SINGLETON, definition.scope());
        assertEquals(ComponentOrigin.APPLICATION, definition.origin());
        assertEquals("UserService(UserRepository)", definition.description());
        assertEquals(List.of(UserRepository.class), definition.dependencies());
        assertTrue(definition.owned());
    }

    @Test
    void registeredScopeIsUsed() {
        ContainerBuilder builder = ContainerBuilder.create().component(UserService.class, ComponentScope.PROTOTYPE);

        assertEquals(ComponentScope.PROTOTYPE, definition(builder, UserService.class).scope());
    }

    @Test
    void multipleConstructorsAreRejected() {
        DependencyException failure = assertThrows(DependencyException.class,
                () -> definition(ContainerBuilder.create(), TwoConstructors.class));

        assertEquals("Dependency Error\n\nCannot create TwoConstructors.\n\nProblem:\n"
                + "TwoConstructors has 2 public constructors.\nDeclare exactly one constructor or register a factory.",
                failure.getMessage());
    }

    @Test
    void privateConstructorsAreNeverEligible() {
        DependencyException failure = assertThrows(DependencyException.class,
                () -> definition(ContainerBuilder.create(), PrivateConstructorOnly.class));
        assertTrue(failure.getMessage().contains("PrivateConstructorOnly has no public constructor."));

        assertEquals(List.of(UserRepository.class),
                definition(ContainerBuilder.create(), PublicAndPrivateConstructors.class).dependencies());
    }

    @Test
    void nonPublicAndInnerClassesAreRejected() {
        assertTrue(assertThrows(DependencyException.class,
                () -> definition(ContainerBuilder.create(), PackagePrivateClass.class))
                .getMessage().contains("PackagePrivateClass is not public."));
        assertTrue(assertThrows(DependencyException.class,
                () -> definition(ContainerBuilder.create(), InnerClass.class))
                .getMessage().contains("InnerClass is not a top-level or static nested class."));
    }

    @Test
    void parameterizedDependenciesAreRejected() {
        DependencyException failure = assertThrows(DependencyException.class,
                () -> definition(ContainerBuilder.create(), NeedsList.class));

        assertTrue(failure.getMessage().contains("Parameterized dependencies are not supported yet"),
                failure.getMessage());
    }

    @Test
    void primitivesCannotBeInjected() {
        assertTrue(assertThrows(DependencyException.class,
                () -> definition(ContainerBuilder.create(), int.class))
                .getMessage().contains("int cannot be injected."));
    }

    @Test
    void interfaceWithoutProviderIsMissing() {
        DependencyException failure = assertThrows(DependencyException.class,
                () -> definition(ContainerBuilder.create(), PaymentGateway.class));

        assertEquals("Dependency Error DHOLE-DI-001\n\nNo provider found for PaymentGateway.", failure.getMessage());
    }

    @Test
    void abstractClassWithoutProviderIsMissing() {
        assertThrows(DependencyException.class, () -> definition(ContainerBuilder.create(), AbstractStorage.class));
    }

    @Test
    void interfaceWithOneRegisteredImplementationIsInferred() {
        ContainerBuilder builder = ContainerBuilder.create().component(StripePaymentGateway.class);

        assertEquals(StripePaymentGateway.class, definition(builder, PaymentGateway.class).type());
    }

    @Test
    void interfaceWithSeveralRegisteredImplementationsIsAmbiguousInEitherRegistrationOrder() {
        for (ContainerBuilder builder : List.of(
                ContainerBuilder.create().component(StripePaymentGateway.class).component(PaypalPaymentGateway.class),
                ContainerBuilder.create().component(PaypalPaymentGateway.class).component(StripePaymentGateway.class))) {
            AmbiguousDependencyException failure = assertThrows(AmbiguousDependencyException.class,
                    () -> definition(builder, PaymentGateway.class));

            assertEquals("Dependency Error DHOLE-DI-002\n\nMultiple providers found for PaymentGateway:\n\n"
                    + "- PaypalPaymentGateway\n- StripePaymentGateway\n\nDeclare an explicit binding.",
                    failure.getMessage());
        }
    }

    @Test
    void explicitBindingSelectsAmongSeveralImplementations() {
        ContainerBuilder builder = ContainerBuilder.create()
                .component(StripePaymentGateway.class)
                .component(PaypalPaymentGateway.class)
                .bind(PaymentGateway.class).to(PaypalPaymentGateway.class);

        assertEquals(PaypalPaymentGateway.class, definition(builder, PaymentGateway.class).type());
    }

    @Test
    void secondBindingForATypeIsABindingConflict() {
        ContainerBuilder builder = ContainerBuilder.create().bind(PaymentGateway.class).to(StripePaymentGateway.class);

        DependencyException failure = assertThrows(DependencyException.class,
                () -> builder.bind(PaymentGateway.class).to(PaypalPaymentGateway.class));

        assertTrue(failure.getMessage().startsWith("Binding Conflict"), failure.getMessage());
        assertThrows(DependencyException.class,
                () -> builder.provide(PaymentGateway.class, context -> new StripePaymentGateway()));
    }

    @Test
    void duplicateComponentRegistrationIsRejected() {
        ContainerBuilder builder = ContainerBuilder.create().component(UserService.class);

        assertThrows(DependencyException.class, () -> builder.component(UserService.class));
    }

    @Test
    void abstractTypesCannotBeRegisteredOrBoundAsImplementations() {
        assertThrows(DependencyException.class, () -> ContainerBuilder.create().component(PaymentGateway.class));
        assertThrows(DependencyException.class, () -> ContainerBuilder.create().component(AbstractStorage.class));
        @SuppressWarnings({"unchecked", "rawtypes"})
        Class<PaymentGateway> raw = (Class) PaymentGateway.class;
        assertThrows(DependencyException.class, () -> ContainerBuilder.create().bind(PaymentGateway.class).to(raw));
    }

    @Test
    void instanceBindingDefinesAnUnownedSingletonUnlessOwnershipIsTransferred() {
        Clock clock = Clock.systemUTC();
        ComponentDefinition external = definition(ContainerBuilder.create().bind(Clock.class).toInstance(clock),
                Clock.class);
        ComponentDefinition owned = definition(ContainerBuilder.create().bind(Clock.class).toOwnedInstance(clock),
                Clock.class);

        assertEquals(ComponentDefinition.Kind.INSTANCE, external.kind());
        assertEquals(ComponentScope.SINGLETON, external.scope());
        assertEquals(false, external.owned());
        assertEquals(true, owned.owned());
    }

    @Test
    void factoryBindingDefinesAFactoryComponent() throws Exception {
        UserRepository repository = new UserRepository();
        ComponentDefinition definition = definition(ContainerBuilder.create()
                .provide(UserRepository.class, ComponentScope.PROTOTYPE, context -> repository), UserRepository.class);

        assertEquals(ComponentDefinition.Kind.FACTORY, definition.kind());
        assertEquals(ComponentOrigin.FACTORY, definition.origin());
        assertEquals(ComponentScope.PROTOTYPE, definition.scope());
        assertSame(repository, definition.instantiator().create(List.of(), null));
    }

    @Test
    void registrationsKeepTheirOrder() {
        ComponentRegistry registry = ContainerBuilder.create()
                .component(UserService.class)
                .bind(PaymentGateway.class).to(StripePaymentGateway.class)
                .provide(Clock.class, context -> Clock.systemUTC())
                .registry();

        assertEquals(List.of(UserService.class, PaymentGateway.class, Clock.class), registry.registrations());
    }

    private static ComponentDefinition definition(ContainerBuilder builder, Class<?> type) {
        return builder.registry().definition(type, List.of(type));
    }
}
