package org.dhole.internal.web;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Parses the type names written in {@code routes.idx} into {@link Type}s: binary class names,
 * primitives, generic arguments {@code <a,b>} without spaces and {@code []} array suffixes.
 */
final class TypeNames {

    private static final Map<String, Class<?>> PRIMITIVES = Map.of(
            "boolean", boolean.class, "byte", byte.class, "char", char.class, "short", short.class,
            "int", int.class, "long", long.class, "float", float.class, "double", double.class,
            "void", void.class);

    private final String text;
    private final ClassLoader loader;
    private int position;

    private TypeNames(String text, ClassLoader loader) {
        this.text = text;
        this.loader = loader;
    }

    /**
     * @throws IllegalArgumentException if the name is malformed
     * @throws ClassNotFoundException if a named class does not exist
     */
    static Type parse(String text, ClassLoader loader) throws ClassNotFoundException {
        TypeNames parser = new TypeNames(Objects.requireNonNull(text, "text"), loader);
        Type type = parser.type();
        if (parser.position != text.length()) {
            throw new IllegalArgumentException("Unexpected '" + text.substring(parser.position) + "' in type " + text);
        }
        return type;
    }

    private Type type() throws ClassNotFoundException {
        int start = position;
        while (position < text.length() && "<>,[]".indexOf(text.charAt(position)) < 0) {
            position++;
        }
        String name = text.substring(start, position);
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Missing type name in " + text);
        }
        Class<?> raw = PRIMITIVES.containsKey(name) ? PRIMITIVES.get(name) : Class.forName(name, false, loader);
        Type type = raw;
        if (position < text.length() && text.charAt(position) == '<') {
            position++;
            List<Type> arguments = new ArrayList<>();
            arguments.add(type());
            while (position < text.length() && text.charAt(position) == ',') {
                position++;
                arguments.add(type());
            }
            expect('>');
            if (arguments.size() != raw.getTypeParameters().length) {
                throw new IllegalArgumentException("Wrong number of type arguments for " + name + " in " + text);
            }
            type = new Parameterized(raw, arguments.toArray(Type[]::new));
        }
        while (position < text.length() && text.charAt(position) == '[') {
            position++;
            expect(']');
            type = type instanceof Class<?> component ? component.arrayType() : new GenericArray(type);
        }
        return type;
    }

    private void expect(char expected) {
        if (position >= text.length() || text.charAt(position) != expected) {
            throw new IllegalArgumentException("Expected '" + expected + "' in type " + text);
        }
        position++;
    }

    private record Parameterized(Class<?> raw, Type[] arguments) implements ParameterizedType {

        @Override
        public Type[] getActualTypeArguments() {
            return arguments.clone();
        }

        @Override
        public Type getRawType() {
            return raw;
        }

        @Override
        public Type getOwnerType() {
            return raw.getDeclaringClass();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ParameterizedType type && raw.equals(type.getRawType())
                    && Objects.equals(getOwnerType(), type.getOwnerType())
                    && Arrays.equals(arguments, type.getActualTypeArguments());
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(arguments) ^ Objects.hashCode(getOwnerType()) ^ raw.hashCode();
        }

        @Override
        public String getTypeName() {
            return raw.getName() + "<" + String.join(", ", Arrays.stream(arguments).map(Type::getTypeName).toList()) + ">";
        }

        @Override
        public String toString() {
            return getTypeName();
        }
    }

    private record GenericArray(Type component) implements GenericArrayType {

        @Override
        public Type getGenericComponentType() {
            return component;
        }

        @Override
        public String getTypeName() {
            return component.getTypeName() + "[]";
        }

        @Override
        public String toString() {
            return getTypeName();
        }
    }
}
