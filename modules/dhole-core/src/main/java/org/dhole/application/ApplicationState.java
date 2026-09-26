package org.dhole.application;

/**
 * Lifecycle state of a Dhole application.
 */
public enum ApplicationState {
    CREATED,
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    FAILED
}
