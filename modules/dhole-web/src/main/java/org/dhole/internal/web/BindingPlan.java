package org.dhole.internal.web;

import java.util.List;
import java.util.Objects;

import org.dhole.serialization.TypeRef;

/**
 * The precomputed way to call one typed route: a resolver per handler parameter, in order, and the
 * declared response type used to choose and drive the response serializer.
 */
final class BindingPlan {

    private final List<ParameterResolver> resolvers;
    private final TypeRef<?> response;

    BindingPlan(List<ParameterResolver> resolvers, TypeRef<?> response) {
        this.resolvers = List.copyOf(resolvers);
        this.response = Objects.requireNonNull(response, "response");
    }

    /**
     * Resolves every argument.
     *
     * @throws org.dhole.web.BindingException if an argument cannot be bound (400)
     * @throws UnsupportedMediaTypeException if the body's media type is not supported (415)
     */
    Object[] bind(RoutedRequest request) {
        Object[] arguments = new Object[resolvers.size()];
        for (int index = 0; index < arguments.length; index++) {
            arguments[index] = resolvers.get(index).resolve(request);
        }
        return arguments;
    }

    TypeRef<?> response() {
        return response;
    }
}
