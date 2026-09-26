package org.dhole.internal.di;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The request components of one request. Each {@code REQUEST} component is created at most once per
 * scope and never shared with another scope. {@link #close()} closes the request resources the scope
 * owns, in reverse creation order, exactly once. Passed explicitly through the request pipeline; no
 * thread-local state.
 *
 * <p>Public only for the internal web runtime; not application API.
 */
public final class RequestScope implements AutoCloseable {

    private final DependencyContainer container;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<ComponentDefinition, Object> instances = new HashMap<>();
    private final List<DependencyContainer.OwnedResource> owned = new ArrayList<>();
    private boolean closed;

    RequestScope(DependencyContainer container) {
        this.container = Objects.requireNonNull(container, "container");
    }

    /**
     * Returns an instance of {@code type} for this request.
     *
     * @throws IllegalStateException if the scope is closed
     * @throws org.dhole.di.DependencyException if {@code type} cannot be provided
     */
    public <T> T resolve(Class<T> type) {
        lock.lock();
        try {
            if (closed) {
                throw new IllegalStateException("The request scope is closed.");
            }
            return container.resolve(type, this);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Closes the request resources this scope owns, in reverse creation order, attempting every one.
     * Closing twice does nothing.
     *
     * @throws org.dhole.di.DependencyException listing the resources that failed to close
     */
    @Override
    public void close() {
        List<DependencyContainer.OwnedResource> resources;
        lock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            resources = List.copyOf(owned);
            owned.clear();
            instances.clear();
        } finally {
            lock.unlock();
        }
        DependencyContainer.closeAll(resources);
    }

    Object instance(ComponentDefinition definition) {
        return instances.get(definition);
    }

    void publish(Map<ComponentDefinition, Object> created, List<DependencyContainer.OwnedResource> resources) {
        instances.putAll(created);
        owned.addAll(resources);
    }
}
