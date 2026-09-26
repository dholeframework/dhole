package org.dhole.internal.module;

/**
 * Starts and stops the runtime of one framework module (MODULE_SYSTEM.md §29). Named by
 * {@code META-INF/dhole/modules.idx} and created through its public no-argument constructor.
 *
 * <p>Internal activation contract, not the public module or plugin API.
 */
public interface ModuleActivator {

    /**
     * Acquires the module's runtime resources; the application is not ready until every module
     * has started.
     */
    void start(ActivationContext context) throws Exception;

    /**
     * Releases what {@link #start} acquired. Called once, in reverse activation order, for every
     * module that started.
     */
    void stop() throws Exception;
}
