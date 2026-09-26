package org.dhole.internal.web;

import java.io.ByteArrayInputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.dhole.http.Request;
import org.dhole.internal.routing.Route;
import org.dhole.internal.routing.TypedHandler;
import org.dhole.routing.RoutingException;
import org.dhole.serialization.MediaType;
import org.dhole.serialization.SerializationException;
import org.dhole.serialization.Serializer;
import org.dhole.serialization.SerializerRegistry;
import org.dhole.serialization.TypeRef;
import org.dhole.web.BindingException;
import org.dhole.web.Body;
import org.dhole.web.Header;
import org.dhole.web.Path;
import org.dhole.web.Query;

/**
 * Builds the {@link BindingPlan} of a typed route from its build-time metadata, at startup. The
 * sources were decided by the metadata compiler; this class only checks that the metadata still
 * matches the registered handler and precomputes a resolver per parameter. Handler signatures are
 * never rediscovered by reflection.
 */
final class ParameterBinder {

    private final RouteMetadata metadata;
    private final SerializerRegistry serializers;

    ParameterBinder(RouteMetadata metadata, SerializerRegistry serializers) {
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.serializers = Objects.requireNonNull(serializers, "serializers");
    }

    /**
     * @throws RoutingException if the route's metadata is missing, stale or unusable ("rebuild")
     */
    BindingPlan plan(Route route, ClassLoader loader) {
        TypedHandler handler = route.typed().orElseThrow();
        String name = route + " of " + route.declaredBy();
        RouteMetadata.Entry entry = metadata.route(route.controller(), route.method(), route.path())
                .orElseThrow(() -> stale(name, "it has no build-time metadata in " + RouteMetadata.LOCATION
                        + ". Typed routes must be declared in routes() with a constant path"));
        if (entry.parameters().size() != handler.arity()) {
            throw stale(name, "its metadata describes " + entry.parameters().size() + " parameter(s) but the handler takes "
                    + handler.arity());
        }
        List<ParameterResolver> resolvers = new ArrayList<>();
        for (RouteMetadata.Parameter parameter : entry.parameters()) {
            resolvers.add(resolver(name, route.path(), parameter, type(name, parameter.type(), loader)));
        }
        return new BindingPlan(resolvers, TypeRef.of(type(name, entry.response(), loader)));
    }

    private ParameterResolver resolver(String route, String path, RouteMetadata.Parameter parameter, Type type) {
        String parameterName = parameter.name();
        return switch (parameter.source()) {
            case REQUEST -> {
                if (type != Request.class) {
                    throw stale(route, "parameter '" + parameterName + "' is not a Request");
                }
                yield request -> request;
            }
            case PATH -> {
                if (!path.contains("{" + parameterName + "}")) {
                    throw stale(route, "the path has no {" + parameterName + "} placeholder");
                }
                boolean wrapped = wrapped(type, Path.class);
                Class<?> scalar = scalar(route, parameterName, wrapped ? argument(type) : type);
                yield request -> {
                    Object value = convert(request.pathParameter(parameterName), scalar, parameterName, "path");
                    return wrapped ? Path.of(value) : value;
                };
            }
            case QUERY -> {
                Class<?> scalar = scalar(route, parameterName, wrappedArgument(route, parameterName, type, Query.class));
                yield request -> Query.of(parameterName,
                        optional(request.queryParameter(parameterName), scalar, parameterName, "query"));
            }
            case HEADER -> {
                Class<?> scalar = scalar(route, parameterName, wrappedArgument(route, parameterName, type, Header.class));
                String header = kebab(parameterName);
                yield request -> Header.of(parameterName, optional(request.header(header), scalar, parameterName, "header"));
            }
            case BODY -> {
                boolean wrapped = wrapped(type, Body.class);
                TypeRef<?> body = TypeRef.of(wrapped ? argument(type) : type);
                yield request -> {
                    Object value = body(request, body);
                    return wrapped ? Body.of(value) : value;
                };
            }
        };
    }

    private Object body(Request request, TypeRef<?> type) {
        String contentType = request.header("Content-Type");
        Optional<Serializer> serializer = Optional.empty();
        if (contentType != null) {
            try {
                serializer = serializers.forMediaType(MediaType.parse(contentType));
            } catch (IllegalArgumentException e) {
                serializer = Optional.empty();
            }
        }
        if (serializer.isEmpty()) {
            throw new UnsupportedMediaTypeException("Unsupported request Content-Type");
        }
        byte[] content = request.body();
        if (content.length == 0) {
            throw new BindingException("Missing request body");
        }
        try {
            Object value = serializer.get().deserialize(new ByteArrayInputStream(content), type);
            if (value == null) {
                throw new BindingException("Missing request body");
            }
            return value;
        } catch (SerializationException e) {
            throw new BindingException("Invalid request body: " + e.getMessage(), e);
        }
    }

    private static Object convert(String value, Class<?> type, String name, String source) {
        if (value == null) {
            throw new BindingException("Missing " + source + " parameter '" + name + "'");
        }
        try {
            return ConversionService.convert(value, type);
        } catch (IllegalArgumentException e) {
            throw new BindingException("Invalid " + source + " parameter '" + name + "': expected "
                    + ConversionService.describe(type), e);
        }
    }

    private static Object optional(String value, Class<?> type, String name, String source) {
        return value == null ? null : convert(value, type, name, source);
    }

    private static Class<?> scalar(String route, String parameter, Type type) {
        if (!(type instanceof Class<?> scalar) || !ConversionService.supports(scalar)) {
            throw stale(route, "parameter '" + parameter + "' has the unsupported type " + type.getTypeName());
        }
        return scalar;
    }

    private static Type wrappedArgument(String route, String parameter, Type type, Class<?> wrapper) {
        if (!wrapped(type, wrapper)) {
            throw stale(route, "parameter '" + parameter + "' is not a " + wrapper.getSimpleName() + "<T>");
        }
        return argument(type);
    }

    private static boolean wrapped(Type type, Class<?> wrapper) {
        return type instanceof ParameterizedType parameterized && parameterized.getRawType() == wrapper;
    }

    private static Type argument(Type type) {
        return ((ParameterizedType) type).getActualTypeArguments()[0];
    }

    private static Type type(String route, String name, ClassLoader loader) {
        try {
            return TypeNames.parse(name, loader);
        } catch (ClassNotFoundException | IllegalArgumentException e) {
            throw stale(route, "its metadata names the type " + name + ", which cannot be loaded");
        }
    }

    /**
     * Converts a parameter name to its conventional header name: {@code acceptLanguage} to
     * {@code accept-language} (header names are case-insensitive).
     */
    static String kebab(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    }

    private static RoutingException stale(String route, String problem) {
        return new RoutingException("Metadata Error\n\nTyped route " + route + " cannot be bound: " + problem
                + ".\n\nRebuild the application.");
    }
}
