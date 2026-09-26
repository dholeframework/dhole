package org.dhole.internal.web;

/**
 * Where a typed handler parameter is read from, decided at build time (PARAMETER_BINDING.md §38).
 */
enum ParameterSource {
    PATH,
    QUERY,
    HEADER,
    BODY,
    REQUEST
}
