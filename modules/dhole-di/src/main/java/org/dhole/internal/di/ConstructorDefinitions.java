package org.dhole.internal.di;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds constructor {@link ComponentDefinition}s by reflection. The only reflective code of the
 * container, limited to explicitly requested types; generated metadata can replace it.
 *
 * <p>Rule: a component has exactly one eligible constructor. Eligible constructors are the public
 * constructors of a public, concrete, top-level or static nested class. Private and
 * package-private constructors are never used (no {@code setAccessible}); package-private
 * construction is left to generated factories. There is no constructor-selection heuristic.
 */
final class ConstructorDefinitions {

    private ConstructorDefinitions() {
    }

    /**
     * @throws UnusableComponentException describing why {@code type} cannot be constructed
     */
    static ComponentDefinition of(Class<?> type, ComponentScope scope) {
        requireConstructible(type);
        Constructor<?>[] constructors = type.getConstructors();
        if (constructors.length == 0) {
            throw new UnusableComponentException(DependencyMessages.name(type) + " has no public constructor.");
        }
        if (constructors.length > 1) {
            throw new UnusableComponentException(DependencyMessages.name(type) + " has " + constructors.length
                    + " public constructors.\nDeclare exactly one constructor or register a factory.");
        }
        Constructor<?> constructor = constructors[0];
        Type[] genericTypes = constructor.getGenericParameterTypes();
        for (Type genericType : genericTypes) {
            if (genericType instanceof ParameterizedType) {
                throw new UnusableComponentException("Constructor " + describe(constructor)
                        + " has the parameterized dependency " + genericType.getTypeName()
                        + ".\nParameterized dependencies are not supported yet; register a factory.");
            }
        }
        return new ComponentDefinition(type, ComponentDefinition.Kind.CONSTRUCTOR, scope,
                ComponentOrigin.APPLICATION, describe(constructor), List.of(constructor.getParameterTypes()),
                (dependencies, context) -> invoke(constructor, dependencies), true);
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
            throw new UnusableComponentException(name + " is not a top-level or static nested class.");
        }
        if (!Modifier.isPublic(type.getModifiers())) {
            throw new UnusableComponentException(name + " is not public.");
        }
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
