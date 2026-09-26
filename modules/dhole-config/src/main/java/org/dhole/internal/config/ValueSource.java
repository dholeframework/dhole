package org.dhole.internal.config;

/**
 * Where a configuration value came from, in precedence order.
 */
enum ValueSource {
    PROCESS,
    DOT_ENV,
    DEFAULT
}
