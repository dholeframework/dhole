package org.dhole.application;

/**
 * Runtime context of a single Dhole application instance.
 *
 * <p>Each application owns exactly one context. A context is not a global singleton: several
 * contexts may exist in the same process, and a new context is created on each development
 * restart.
 */
public interface ApplicationContext {
}
