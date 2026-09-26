package org.dhole.internal.di;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.dhole.di.CircularDependencyException;
import org.dhole.di.DependencyException;

/**
 * Creates components from validated {@link DependencyGraph}s.
 *
 * <p>Every resolution first builds (or reuses) the graph of the requested type, so missing,
 * ambiguous, unusable and circular dependencies fail before any instance is created. Singletons
 * are created once per container; prototypes on every resolution. Separate containers never share
 * instances.
 *
 * <p>Thread safety: a resolution that has to create a singleton, or may run a factory, holds the
 * container lock, so each singleton is created exactly once. Resolutions that only create
 * prototypes over existing singletons run without the lock. Singletons created by a resolution
 * become visible to other resolutions only when that resolution succeeds.
 *
 * <p>Resources: the container owns the {@link AutoCloseable} singletons it creates and instances
 * whose ownership was transferred with {@code toOwnedInstance}; it never closes external instances.
 * Prototypes are owned by whoever receives them. {@link #close()} closes owned resources in reverse
 * creation order, so dependents close before their dependencies. A resolution or startup that
 * fails closes, in reverse creation order, every owned resource it created (prototypes included)
 * and rethrows the original failure with cleanup failures attached as suppressed exceptions.
 */
final class DependencyContainer implements AutoCloseable {

    private final ComponentRegistry registry;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<ComponentDefinition, Object> singletons = new ConcurrentHashMap<>();
    private final Map<Class<?>, DependencyGraph> graphs = new ConcurrentHashMap<>();
    private final DependencyGraph registered;
    private final List<OwnedResource> owned = new ArrayList<>();
    private volatile boolean closed;

    private DependencyContainer(ComponentRegistry registry) {
        this.registry = registry;
        this.registered = new DependencyGraphBuilder(registry).build(registry.registrations());
    }

    /**
     * Validates every registration and creates the registered singletons, dependencies first.
     *
     * @throws DependencyException if the registrations are invalid or a singleton cannot be created
     */
    static DependencyContainer start(ComponentRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        DependencyContainer container = new DependencyContainer(registry);
        container.startSingletons();
        return container;
    }

    /**
     * Returns an instance of {@code type}, creating it and its dependencies as needed.
     *
     * @throws DependencyException if {@code type} cannot be provided
     */
    <T> T resolve(Class<T> type) {
        Objects.requireNonNull(type, "type");
        ensureOpen();
        DependencyNode node = graph(type).roots().get(0);
        Object cached = singletons.get(node.definition());
        if (cached != null) {
            return type.cast(cached);
        }
        if (!needsLock(node)) {
            return type.cast(new Resolution().run(node));
        }
        lock.lock();
        try {
            ensureOpen();
            return type.cast(new Resolution().run(node));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the validated graph of {@code root}.
     */
    DependencyGraph graph(Class<?> root) {
        Objects.requireNonNull(root, "root");
        return graphs.computeIfAbsent(root, type -> new DependencyGraphBuilder(registry).build(List.of(type)));
    }

    /**
     * Returns the validated graph of every registered component and binding.
     */
    DependencyGraph registeredGraph() {
        return registered;
    }

    /**
     * Closes every owned resource in reverse creation order. Every resource is closed even if some
     * fail; failures are reported together. Closing twice does nothing.
     *
     * @throws DependencyException listing the resources that failed to close, each failure attached
     *         as a suppressed exception
     */
    @Override
    public void close() {
        lock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            List<OwnedResource> failed = new ArrayList<>();
            List<Exception> failures = new ArrayList<>();
            for (int index = owned.size() - 1; index >= 0; index--) {
                OwnedResource resource = owned.get(index);
                try {
                    resource.instance().close();
                } catch (Exception e) {
                    failed.add(resource);
                    failures.add(e);
                }
            }
            owned.clear();
            singletons.clear();
            if (!failures.isEmpty()) {
                DependencyException failure = new DependencyException("Dependency Error\n\nFailed to close "
                        + failed.size() + " component(s):\n"
                        + String.join("\n", failed.stream().map(r -> "- " + DependencyMessages.name(r.type())).toList()));
                failures.forEach(failure::addSuppressed);
                throw failure;
            }
        } finally {
            lock.unlock();
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("The dependency container is closed.");
        }
    }

    private void startSingletons() {
        lock.lock();
        try {
            Resolution resolution = new Resolution();
            resolution.run(() -> {
                for (DependencyNode node : registered.dependencyOrder()) {
                    if (node.definition().scope() == ComponentScope.SINGLETON) {
                        resolution.create(node);
                    }
                }
                return null;
            });
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether creating {@code node} may create a singleton or run a factory.
     */
    private boolean needsLock(DependencyNode node) {
        ComponentDefinition definition = node.definition();
        if (definition.scope() == ComponentScope.SINGLETON) {
            return !singletons.containsKey(definition);
        }
        if (definition.kind() == ComponentDefinition.Kind.FACTORY) {
            return true;
        }
        return node.dependencies().stream().anyMatch(this::needsLock);
    }

    /**
     * One top-level resolution: tracks what it creates so it can publish or discard it.
     */
    private final class Resolution implements FactoryContext {

        private final Map<ComponentDefinition, Object> pendingSingletons = new LinkedHashMap<>();
        private final List<Class<?>> path = new ArrayList<>();
        private final List<ComponentDefinition> inProgress = new ArrayList<>();
        private final List<OwnedResource> created = new ArrayList<>();

        Object run(DependencyNode root) {
            return run(() -> create(root));
        }

        /**
         * Runs {@code action}; publishes the created singletons and their owned resources on
         * success, closes every owned resource it created on failure.
         */
        Object run(Supplier<Object> action) {
            Object result;
            try {
                result = action.get();
            } catch (RuntimeException | Error failure) {
                rollback(failure);
                throw failure;
            }
            singletons.putAll(pendingSingletons);
            for (OwnedResource resource : created) {
                if (resource.singleton()) {
                    owned.add(resource);
                }
            }
            return result;
        }

        private void rollback(Throwable failure) {
            for (int index = created.size() - 1; index >= 0; index--) {
                try {
                    created.get(index).instance().close();
                } catch (Exception cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
        }

        @Override
        public <T> T resolve(Class<T> type) {
            Objects.requireNonNull(type, "type");
            return type.cast(create(graph(type).roots().get(0)));
        }

        Object create(DependencyNode node) {
            ComponentDefinition definition = node.definition();
            if (definition.scope() == ComponentScope.SINGLETON) {
                Object existing = singletons.get(definition);
                if (existing == null) {
                    existing = pendingSingletons.get(definition);
                }
                if (existing != null) {
                    return existing;
                }
            }
            int cycleStart = inProgress.indexOf(definition);
            if (cycleStart >= 0) {
                List<Class<?>> cycle = new ArrayList<>(path.subList(cycleStart, path.size()));
                cycle.add(node.requestedType());
                throw new CircularDependencyException(DependencyMessages.circular(cycle));
            }
            path.add(node.requestedType());
            inProgress.add(definition);
            try {
                List<Object> dependencies = new ArrayList<>();
                for (DependencyNode dependency : node.dependencies()) {
                    dependencies.add(create(dependency));
                }
                Object instance = instantiate(definition, dependencies);
                boolean singleton = definition.scope() == ComponentScope.SINGLETON;
                if (singleton) {
                    pendingSingletons.put(definition, instance);
                }
                if (definition.owned() && instance instanceof AutoCloseable closeable) {
                    created.add(new OwnedResource(definition.type(), closeable, singleton));
                }
                return instance;
            } finally {
                inProgress.remove(inProgress.size() - 1);
                path.remove(path.size() - 1);
            }
        }

        private Object instantiate(ComponentDefinition definition, List<Object> dependencies) {
            Object instance;
            try {
                instance = definition.instantiator().create(dependencies, this);
            } catch (DependencyException e) {
                throw e;
            } catch (Exception e) {
                throw new DependencyException(DependencyMessages.constructionFailed(definition, List.copyOf(path)), e);
            }
            if (instance == null) {
                throw new DependencyException(DependencyMessages.constructionFailed(definition, List.copyOf(path))
                        + "\n\nProblem:\nThe factory returned null.");
            }
            if (!definition.type().isInstance(instance)) {
                throw new DependencyException(DependencyMessages.constructionFailed(definition, List.copyOf(path))
                        + "\n\nProblem:\nThe factory returned a " + instance.getClass().getName() + ".");
            }
            return instance;
        }
    }

    private record OwnedResource(Class<?> type, AutoCloseable instance, boolean singleton) {
    }
}
