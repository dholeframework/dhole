package org.dhole.internal.web;

/**
 * How much the web runtime reveals about unexpected errors (ERRORS.md §12). An internal composition
 * option until the mode derives from the application environment.
 */
enum RuntimeMode {

    /**
     * {@code 500} responses may carry the exception type and message in {@code details}.
     */
    DEVELOPMENT,

    /**
     * {@code 500} responses carry only a generic safe message.
     */
    PRODUCTION
}
