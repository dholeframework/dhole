package org.dhole.internal.web;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.dhole.http.HttpServer;
import org.dhole.internal.di.ComponentMetadata;
import org.dhole.internal.di.ContainerBuilder;
import org.dhole.internal.di.DependencyContainer;
import org.dhole.internal.http.JdkHttpServer;
import org.dhole.internal.json.JacksonJsonSerializer;
import org.dhole.internal.routing.Route;
import org.dhole.internal.routing.RouteMatcher;
import org.dhole.internal.routing.RouteRegistry;
import org.dhole.internal.validation.DefaultValidator;
import org.dhole.internal.validation.ValidationMetadata;
import org.dhole.internal.validation.ValidationRegistry;
import org.dhole.serialization.SerializerRegistry;
import org.dhole.validation.Validator;
import org.dhole.web.Controller;

/**
 * Internal M5 composition of the web stack; not application API and not the final bootstrap
 * (CORE_ARCHITECTURE.md, M5 decisions). It orchestrates existing subsystems and reimplements none:
 * metadata (M4), dependency injection (M3), routing and the HTTP server adapter.
 *
 * <p>Startup order; the server listens only after every step succeeds:
 * <ol>
 *   <li>controller roots: indexed classes whose supertypes include {@link Controller};</li>
 *   <li>dependency container from the component index;</li>
 *   <li>the reachable graph of every controller validated (DHOLE-DI-001..003 fail here);</li>
 *   <li>controllers created and their routes registered;</li>
 *   <li>route conflicts rejected;</li>
 *   <li>a binding plan built for every typed route from {@code routes.idx}; missing, stale or
 *       unusable metadata fails here ("rebuild the application"), as do the rules of every
 *       {@code Validatable} body type;</li>
 *   <li>request pipeline assembled and the server started.</li>
 * </ol>
 * A startup failure closes what was already acquired and rethrows the original failure. Indexed
 * classes that are neither controllers nor reachable from them are never validated or created.
 */
final class WebRuntime implements AutoCloseable {

    static final String CONTROLLER = Controller.class.getName();

    private final HttpServer server;
    private final DependencyContainer container;
    private final RouteMatcher routes;

    private WebRuntime(HttpServer server, DependencyContainer container, RouteMatcher routes) {
        this.server = server;
        this.container = container;
        this.routes = routes;
    }

    /**
     * Starts the application found in {@code loader}'s component index on the JDK server adapter.
     *
     * @param port the port to bind, {@code 0} for an ephemeral port
     */
    static WebRuntime start(ClassLoader loader, String host, int port) {
        return start(loader, host, port, RuntimeMode.PRODUCTION);
    }

    static WebRuntime start(ClassLoader loader, String host, int port, RuntimeMode mode) {
        return start(ComponentMetadata.load(loader), RouteMetadata.load(loader), ContainerBuilder.create(),
                new JdkHttpServer(host, port), Options.defaults().validation(ValidationMetadata.load(loader)).mode(mode));
    }

    /**
     * Starts without typed route metadata: only raw {@code Request} routes can be served.
     */
    static WebRuntime start(ComponentMetadata metadata, ContainerBuilder components, HttpServer server) {
        return start(metadata, RouteMetadata.empty(), components, server);
    }

    static WebRuntime start(ComponentMetadata metadata, RouteMetadata routeMetadata, ContainerBuilder components,
            HttpServer server) {
        return start(metadata, routeMetadata, components, server, Options.defaults());
    }

    static WebRuntime start(ComponentMetadata metadata, RouteMetadata routeMetadata, ContainerBuilder components,
            HttpServer server, Options options) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(routeMetadata, "routeMetadata");
        Objects.requireNonNull(components, "components");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(options, "options");
        List<Class<?>> controllers = metadata.providersOf(CONTROLLER).stream().map(metadata::loadClass).toList();
        ValidationRegistry validations = new ValidationRegistry(options.validation());
        Validator validator = new DefaultValidator(validations);
        DependencyContainer container = components.metadata(metadata).bind(Validator.class).toInstance(validator).build();
        try {
            for (Class<?> controller : controllers) {
                container.graph(controller);
            }
            RouteRegistry registry = new RouteRegistry();
            Map<String, ClassLoader> loaders = new HashMap<>();
            for (Class<?> controller : controllers) {
                loaders.put(controller.getName(), controller.getClassLoader());
                ((Controller) container.resolve(controller)).routes(registry.router(controller.getName()));
            }
            RouteMatcher routes = registry.build();
            SerializerRegistry serializers = SerializerRegistry.of(List.of(new JacksonJsonSerializer()));
            ParameterBinder binder = new ParameterBinder(routeMetadata, serializers, validations, validator);
            Map<Route, BindingPlan> plans = new HashMap<>();
            for (Route route : routes.routes()) {
                if (route.typed().isPresent()) {
                    plans.put(route, binder.plan(route, loaders.get(route.controller())));
                }
            }
            server.start(new RequestPipeline(routes, container, plans, serializers, options.errors(), options.mode(),
                    options.log()));
            return new WebRuntime(server, container, routes);
        } catch (RuntimeException | Error failure) {
            try {
                container.close();
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    /**
     * Returns the bound port of the JDK server adapter.
     *
     * @throws IllegalStateException if another server implementation is used
     */
    int port() {
        if (server instanceof JdkHttpServer jdk) {
            return jdk.port();
        }
        throw new IllegalStateException("The server does not expose its port.");
    }

    RouteMatcher routes() {
        return routes;
    }

    /**
     * Stops the server, then closes the container's resources; both are attempted.
     */
    @Override
    public void close() {
        List<RuntimeException> failures = new ArrayList<>();
        try {
            server.stop();
        } catch (RuntimeException e) {
            failures.add(e);
        }
        try {
            container.close();
        } catch (RuntimeException e) {
            failures.add(e);
        }
        if (!failures.isEmpty()) {
            RuntimeException failure = failures.get(0);
            failures.subList(1, failures.size()).forEach(failure::addSuppressed);
            throw failure;
        }
    }

    /**
     * Internal composition options: validation metadata, error handlers, runtime mode and the error
     * log. Defaults: no validation metadata, built-in error handlers, production, standard error.
     */
    record Options(ValidationMetadata validation, ErrorHandlerRegistry errors, RuntimeMode mode, PrintStream log) {

        Options {
            Objects.requireNonNull(validation, "validation");
            Objects.requireNonNull(errors, "errors");
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(log, "log");
        }

        static Options defaults() {
            return new Options(ValidationMetadata.empty(), ErrorHandlerRegistry.defaults(), RuntimeMode.PRODUCTION,
                    System.err);
        }

        Options validation(ValidationMetadata validation) {
            return new Options(validation, errors, mode, log);
        }

        Options errors(ErrorHandlerRegistry errors) {
            return new Options(validation, errors, mode, log);
        }

        Options mode(RuntimeMode mode) {
            return new Options(validation, errors, mode, log);
        }

        Options log(PrintStream log) {
            return new Options(validation, errors, mode, log);
        }
    }
}
