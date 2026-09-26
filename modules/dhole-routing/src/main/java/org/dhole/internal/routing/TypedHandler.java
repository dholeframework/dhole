package org.dhole.internal.routing;

import java.util.Objects;

import org.dhole.routing.Handler0;
import org.dhole.routing.Handler1;
import org.dhole.routing.Handler2;
import org.dhole.routing.Handler3;

/**
 * The function registered with {@code RouteBuilder.to(...)}: invoked with arguments already bound
 * by the web layer from the route's build-time binding metadata.
 */
public final class TypedHandler {

    private final int arity;
    private final Object function;

    private TypedHandler(int arity, Object function) {
        this.arity = arity;
        this.function = Objects.requireNonNull(function, "handler");
    }

    static TypedHandler of(Handler0<?> handler) {
        return new TypedHandler(0, handler);
    }

    static TypedHandler of(Handler1<?, ?> handler) {
        return new TypedHandler(1, handler);
    }

    static TypedHandler of(Handler2<?, ?, ?> handler) {
        return new TypedHandler(2, handler);
    }

    static TypedHandler of(Handler3<?, ?, ?, ?> handler) {
        return new TypedHandler(3, handler);
    }

    public int arity() {
        return arity;
    }

    /**
     * @throws IllegalArgumentException if the number of arguments differs from the arity
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Object invoke(Object... arguments) throws Exception {
        if (arguments.length != arity) {
            throw new IllegalArgumentException("Expected " + arity + " arguments, got " + arguments.length);
        }
        return switch (arity) {
            case 0 -> ((Handler0) function).handle();
            case 1 -> ((Handler1) function).handle(arguments[0]);
            case 2 -> ((Handler2) function).handle(arguments[0], arguments[1]);
            default -> ((Handler3) function).handle(arguments[0], arguments[1], arguments[2]);
        };
    }
}
