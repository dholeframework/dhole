package org.dhole.internal.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.dhole.http.HttpServer;
import org.dhole.internal.di.ComponentMetadata;
import org.dhole.internal.di.ContainerBuilder;
import org.dhole.internal.di.DependencyContainer;
import org.dhole.internal.http.JdkHttpServer;
import org.dhole.internal.routing.RouteMatcher;
import org.dhole.internal.routing.RouteRegistry;
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
        return start(ComponentMetadata.load(loader), ContainerBuilder.create(), new JdkHttpServer(host, port));
    }

    static WebRuntime start(ComponentMetadata metadata, ContainerBuilder components, HttpServer server) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(components, "components");
        Objects.requireNonNull(server, "server");
        List<Class<?>> controllers = metadata.providersOf(CONTROLLER).stream().map(metadata::loadClass).toList();
        DependencyContainer container = components.metadata(metadata).build();
        try {
            for (Class<?> controller : controllers) {
                container.graph(controller);
            }
            RouteRegistry registry = new RouteRegistry();
            for (Class<?> controller : controllers) {
                ((Controller) container.resolve(controller)).routes(registry.router(controller.getName()));
            }
            RouteMatcher routes = registry.build();
            server.start(new RequestPipeline(routes, container));
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
}
