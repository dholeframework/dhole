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
 */
final class DependencyContainer {

    private final ComponentRegistry registry;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<ComponentDefinition, Object> singletons = new ConcurrentHashMap<>();
    private final Map<Class<?>, DependencyGraph> graphs = new ConcurrentHashMap<>();
    private final DependencyGraph registered;

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

        Object run(DependencyNode root) {
            return run(() -> create(root));
        }

        Object run(Supplier<Object> action) {
            Object result = action.get();
            singletons.putAll(pendingSingletons);
            return result;
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
                if (definition.scope() == ComponentScope.SINGLETON) {
                    pendingSingletons.put(definition, instance);
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
}
