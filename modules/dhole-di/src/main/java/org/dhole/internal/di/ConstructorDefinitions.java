package org.dhole.internal.di;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.dhole.di.DependencyException;

/**
 * Builds constructor {@link ComponentDefinition}s, from build-time metadata when the type is in the
 * application's component index, otherwise by controlled reflection (library types). Both paths
 * apply the same rule and produce the same definitions and problems.
 *
 * <p>Rule: a component has exactly one eligible constructor. Eligible constructors are the public
 * constructors of a public, concrete, top-level or static nested class. Private and
 * package-private constructors are never used (no {@code setAccessible}); this is an intentional
 * limitation of metadata v1, which has no generated factories. There is no constructor-selection
 * heuristic. Parameterized constructor dependencies are not supported yet.
 */
final class ConstructorDefinitions {

    private ConstructorDefinitions() {
    }

    /**
     * Analyzes {@code type} by reflection.
     *
     * @throws UnusableComponentException describing why {@code type} cannot be constructed
     */
    static ComponentDefinition of(Class<?> type, ComponentScope scope) {
        requireConstructible(type);
        Constructor<?>[] constructors = type.getConstructors();
        if (constructors.length == 0) {
            throw new UnusableComponentException(noPublicConstructor(type));
        }
        if (constructors.length > 1) {
            throw new UnusableComponentException(multiplePublicConstructors(type, constructors.length));
        }
        Constructor<?> constructor = constructors[0];
        Type[] genericTypes = constructor.getGenericParameterTypes();
        for (int index = 0; index < genericTypes.length; index++) {
            if (genericTypes[index] instanceof ParameterizedType) {
                throw new UnusableComponentException(parameterizedDependency(constructor, index));
            }
        }
        return definition(type, constructor, scope, Optional.empty());
    }

    /**
     * Builds the definition recorded in build-time metadata. The recorded constructor is only
     * looked up for invocation; the type is not analyzed again.
     *
     * @throws UnusableComponentException if the metadata records {@code type} as unusable
     * @throws DependencyException if the recorded constructor does not match the class
     */
    static ComponentDefinition fromMetadata(Class<?> type, ComponentMetadata.TypeMetadata metadata,
            ComponentMetadata index, ComponentScope scope) {
        String location = metadata.source().map(source -> "\n\nLocation:\n" + source).orElse("");
        Optional<Constructor<?>> constructor = metadata.constructor().map(parameters -> recorded(type, parameters, index));
        if (metadata.unusable().isPresent()) {
            ComponentMetadata.Unusable unusable = metadata.unusable().get();
            String problem = switch (unusable.reason()) {
                case "not-public" -> notPublic(type);
                case "not-static-nested" -> notStaticNested(type);
                case "no-public-constructor" -> noPublicConstructor(type);
                case "multiple-public-constructors" -> multiplePublicConstructors(type, unusable.argument());
                case "parameterized-dependency" -> parameterizedDependency(constructor.orElseThrow(), unusable.argument());
                default -> throw new IllegalStateException("Unknown reason " + unusable.reason());
            };
            throw new UnusableComponentException(problem + location);
        }
        return definition(type, constructor.orElseThrow(), scope, metadata.source());
    }

    static void requireConstructible(Class<?> type) {
        String name = DependencyMessages.name(type);
        if (type.isPrimitive() || type.isArray()) {
            throw new UnusableComponentException(name + " cannot be injected.");
        }
        if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            throw new UnusableComponentException(name + " is abstract and cannot be constructed.");
        }
        if (type.isEnum() || type.isAnonymousClass() || type.isLocalClass()
                || (type.isMemberClass() && !Modifier.isStatic(type.getModifiers()))) {
            throw new UnusableComponentException(notStaticNested(type));
        }
        if (!Modifier.isPublic(type.getModifiers())) {
            throw new UnusableComponentException(notPublic(type));
        }
    }

    private static ComponentDefinition definition(Class<?> type, Constructor<?> constructor, ComponentScope scope,
            Optional<String> location) {
        return new ComponentDefinition(type, ComponentDefinition.Kind.CONSTRUCTOR, scope,
                ComponentOrigin.APPLICATION, describe(constructor), List.of(constructor.getParameterTypes()),
                (dependencies, context) -> invoke(constructor, dependencies), true, location);
    }

    private static Constructor<?> recorded(Class<?> type, List<String> parameters, ComponentMetadata index) {
        Class<?>[] parameterTypes = parameters.stream().map(index::loadClass).toArray(Class<?>[]::new);
        try {
            return type.getConstructor(parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new DependencyException("Metadata Error\n\nThe component index records the constructor "
                    + DependencyMessages.name(type) + "(" + Arrays.stream(parameterTypes).map(DependencyMessages::name)
                            .collect(Collectors.joining(", "))
                    + "), but " + type.getName() + " does not declare it.\n\nRebuild the application.", e);
        }
    }

    private static String notPublic(Class<?> type) {
        return DependencyMessages.name(type) + " is not public.";
    }

    private static String notStaticNested(Class<?> type) {
        return DependencyMessages.name(type) + " is not a top-level or static nested class.";
    }

    private static String noPublicConstructor(Class<?> type) {
        return DependencyMessages.name(type) + " has no public constructor.";
    }

    private static String multiplePublicConstructors(Class<?> type, int count) {
        return DependencyMessages.name(type) + " has " + count
                + " public constructors.\nDeclare exactly one constructor or register a factory.";
    }

    private static String parameterizedDependency(Constructor<?> constructor, int index) {
        return "Constructor " + describe(constructor) + " has the parameterized dependency "
                + DependencyMessages.name(constructor.getParameterTypes()[index])
                + ".\nParameterized dependencies are not supported yet; register a factory.";
    }

    private static Object invoke(Constructor<?> constructor, List<Object> dependencies) throws Exception {
        try {
            return constructor.newInstance(dependencies.toArray());
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw (Error) cause;
        }
    }

    private static String describe(Constructor<?> constructor) {
        return DependencyMessages.name(constructor.getDeclaringClass()) + "("
                + Arrays.stream(constructor.getParameterTypes()).map(DependencyMessages::name)
                        .collect(Collectors.joining(", "))
                + ")";
    }
}
