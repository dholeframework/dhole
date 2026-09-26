package org.dhole.serialization;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * A full Java type, generics included, for (de)serialization.
 *
 * <pre>{@code
 * TypeRef<List<User>> users = new TypeRef<>() {};
 * TypeRef<User> user = TypeRef.of(User.class);
 * }</pre>
 *
 * @param <T> the described type
 */
public abstract class TypeRef<T> {

    private final Type type;

    /**
     * Captures the type argument of an anonymous subclass: {@code new TypeRef<List<User>>() {}}.
     */
    protected TypeRef() {
        Type superclass = getClass().getGenericSuperclass();
        if (!(superclass instanceof ParameterizedType parameterized)) {
            throw new IllegalStateException("Create a TypeRef with a type argument, for example new TypeRef<List<User>>() {}");
        }
        this.type = parameterized.getActualTypeArguments()[0];
    }

    private TypeRef(Type type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    public static <T> TypeRef<T> of(Class<T> type) {
        return new Explicit<>(type);
    }

    public static TypeRef<?> of(Type type) {
        return new Explicit<>(type);
    }

    public Type type() {
        return type;
    }

    /**
     * Returns the erased class of the type, for example {@code List} for {@code List<User>}.
     */
    public Class<?> rawType() {
        return raw(type);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TypeRef<?> ref && type.equals(ref.type);
    }

    @Override
    public int hashCode() {
        return type.hashCode();
    }

    @Override
    public String toString() {
        return type.getTypeName();
    }

    private static Class<?> raw(Type type) {
        if (type instanceof Class<?> plain) {
            return plain;
        }
        if (type instanceof ParameterizedType parameterized) {
            return raw(parameterized.getRawType());
        }
        if (type instanceof GenericArrayType array) {
            return raw(array.getGenericComponentType()).arrayType();
        }
        return Object.class;
    }

    private static final class Explicit<T> extends TypeRef<T> {

        Explicit(Type type) {
            super(type);
        }
    }
}
