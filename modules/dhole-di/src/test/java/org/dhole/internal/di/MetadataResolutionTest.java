package org.dhole.internal.di;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.dhole.di.AmbiguousDependencyException;
import org.dhole.di.DependencyException;
import org.dhole.internal.di.Fixtures.NeedsList;
import org.dhole.internal.di.Fixtures.OrderService;
import org.dhole.internal.di.Fixtures.PaymentGateway;
import org.dhole.internal.di.Fixtures.PrivateConstructorOnly;
import org.dhole.internal.di.Fixtures.StripePaymentGateway;
import org.dhole.internal.di.Fixtures.TwoConstructors;
import org.dhole.internal.di.Fixtures.UserRepository;
import org.dhole.internal.di.Fixtures.UserService;
import org.junit.jupiter.api.Test;

/**
 * Resolution with a build-time component index, written here in the documented v1 format.
 */
class MetadataResolutionTest {

    private static final String PREFIX = "org.dhole.internal.di.Fixtures$";

    @Test
    void indexedConstructorIsUsedWithTheSameDefinitionAsReflection() {
        ComponentMetadata metadata = index("""
                component %sUserService
                constructor %sUserRepository
                source org/dhole/internal/di/Fixtures.java:17
                """);

        ComponentDefinition fromIndex = definition(metadata, UserService.class);
        ComponentDefinition fromReflection = ConstructorDefinitions.of(UserService.class, ComponentScope.SINGLETON);

        assertEquals(fromReflection.description(), fromIndex.description());
        assertEquals(fromReflection.dependencies(), fromIndex.dependencies());
        assertEquals(fromReflection.kind(), fromIndex.kind());
        assertEquals(java.util.Optional.of("org/dhole/internal/di/Fixtures.java:17"), fromIndex.location());
        assertInstanceOf(UserRepository.class,
                ContainerBuilder.create().metadata(metadata).build().resolve(UserService.class).users);
    }

    @Test
    void indexedUnusableTypesFailWithTheSameProblemAsReflectionPlusLocation() {
        ComponentMetadata metadata = index("""
                component %sTwoConstructors
                unusable multiple-public-constructors 2
                source org/dhole/internal/di/Fixtures.java:60

                component %sPrivateConstructorOnly
                unusable no-public-constructor

                component %sNeedsList
                constructor java.util.List
                unusable parameterized-dependency 0
                """);

        for (Class<?> type : List.of(TwoConstructors.class, PrivateConstructorOnly.class, NeedsList.class)) {
            String reflective = assertThrows(DependencyException.class,
                    () -> definition(ContainerBuilder.create().registry(), type)).getMessage();
            String indexed = assertThrows(DependencyException.class, () -> definition(metadata, type)).getMessage();

            assertTrue(indexed.startsWith(reflective), indexed + "\n---\n" + reflective);
        }
        assertTrue(assertThrows(DependencyException.class, () -> definition(metadata, TwoConstructors.class))
                .getMessage().endsWith("Location:\norg/dhole/internal/di/Fixtures.java:60"));
    }

    @Test
    void indexIsTrustedWithoutRuntimeStructuralAnalysis() {
        // Reflection rejects TwoConstructors (two public constructors); an index recording a single
        // constructor is used as is: only the recorded constructor is looked up, nothing is re-analyzed.
        ComponentMetadata metadata = index("""
                component %sTwoConstructors
                constructor %sUserRepository
                """);

        assertThrows(DependencyException.class, () -> definition(ContainerBuilder.create().registry(), TwoConstructors.class));
        assertEquals(List.of(UserRepository.class), definition(metadata, TwoConstructors.class).dependencies());
    }

    @Test
    void staleIndexFailsClearly() {
        ComponentMetadata metadata = index("""
                component %sUserService
                constructor %sMail
                """);

        DependencyException failure = assertThrows(DependencyException.class, () -> definition(metadata, UserService.class));

        assertEquals("Metadata Error\n\nThe component index records the constructor UserService(Mail), but "
                + UserService.class.getName() + " does not declare it.\n\nRebuild the application.", failure.getMessage());
    }

    @Test
    void indexedClassesAreKnownProvidersOfTheirSupertypes() {
        ComponentMetadata metadata = index("""
                component %sStripePaymentGateway
                constructor
                supertype %sPaymentGateway
                """);

        OrderService orders = ContainerBuilder.create().metadata(metadata).build().resolve(OrderService.class);

        assertInstanceOf(StripePaymentGateway.class, orders.payments);
    }

    @Test
    void severalIndexedProvidersAreAmbiguousWithCodeAndLocation() {
        ComponentMetadata metadata = index("""
                component %sOrderService
                constructor %sPaymentGateway
                source org/dhole/internal/di/Fixtures.java:44

                component %sPaypalPaymentGateway
                constructor
                supertype %sPaymentGateway

                component %sStripePaymentGateway
                constructor
                supertype %sPaymentGateway
                """);

        AmbiguousDependencyException failure = assertThrows(AmbiguousDependencyException.class,
                () -> ContainerBuilder.create().metadata(metadata).build().resolve(OrderService.class));

        assertEquals("Dependency Error DHOLE-DI-002\n\nMultiple providers found for PaymentGateway:\n\n"
                + "- PaypalPaymentGateway\n- StripePaymentGateway\n\n"
                + "Dependency path:\nOrderService\n  -> PaymentGateway\n\n"
                + "Required at:\norg/dhole/internal/di/Fixtures.java:44\n\nDeclare an explicit binding.",
                failure.getMessage());
    }

    @Test
    void explicitBindingStillSelectsAmongIndexedProviders() {
        ComponentMetadata metadata = index("""
                component %sPaypalPaymentGateway
                constructor
                supertype %sPaymentGateway

                component %sStripePaymentGateway
                constructor
                supertype %sPaymentGateway
                """);

        DependencyContainer container = ContainerBuilder.create().metadata(metadata)
                .bind(PaymentGateway.class).to(StripePaymentGateway.class)
                .build();

        assertInstanceOf(StripePaymentGateway.class, container.resolve(PaymentGateway.class));
    }

    @Test
    void missingProviderReportsCodeAndTheRequiringLocation() {
        ComponentMetadata metadata = index("""
                component %sOrderService
                constructor %sPaymentGateway
                source org/dhole/internal/di/Fixtures.java:44
                """);

        DependencyException failure = assertThrows(DependencyException.class,
                () -> ContainerBuilder.create().metadata(metadata).build().resolve(OrderService.class));

        assertEquals("Dependency Error DHOLE-DI-001\n\nNo provider found for PaymentGateway.\n\n"
                + "Dependency path:\nOrderService\n  -> PaymentGateway\n\n"
                + "Required at:\norg/dhole/internal/di/Fixtures.java:44", failure.getMessage());
    }

    @Test
    void indexedClassesAreNotInstantiatedOrLoadedUnlessNeeded() {
        ComponentMetadata metadata = index("""
                component com.example.NotOnTheClasspath
                constructor
                supertype com.example.Unrelated

                component %sUserRepository
                constructor
                """);

        DependencyContainer container = ContainerBuilder.create().metadata(metadata).build();

        assertInstanceOf(UserRepository.class, container.resolve(UserRepository.class));
    }

    @Test
    void typesOutsideTheIndexUseTheReflectiveFallback() {
        ComponentMetadata metadata = index("""
                component %sUserRepository
                constructor
                """);

        assertInstanceOf(UserRepository.class,
                ContainerBuilder.create().metadata(metadata).build().resolve(UserService.class).users);
    }

    private static ComponentMetadata index(String blocks) {
        return ComponentMetadata.parse("dhole-metadata 1\n\n" + blocks.replace("%s", PREFIX),
                MetadataResolutionTest.class.getClassLoader());
    }

    private static ComponentDefinition definition(ComponentMetadata metadata, Class<?> type) {
        return definition(ContainerBuilder.create().metadata(metadata).registry(), type);
    }

    private static ComponentDefinition definition(ComponentRegistry registry, Class<?> type) {
        return registry.definition(type, List.of(type));
    }
}
