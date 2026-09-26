package org.dhole.internal.web;

/**
 * Produces one handler argument from a request. Resolvers are chosen once per route at startup
 * (PARAMETER_BINDING.md §35), never searched per request.
 */
@FunctionalInterface
interface ParameterResolver {

    /**
     * @throws org.dhole.web.BindingException if the value is missing or cannot be converted (400)
     * @throws UnsupportedMediaTypeException if the body's media type has no serializer (415)
     */
    Object resolve(RoutedRequest request);
}
