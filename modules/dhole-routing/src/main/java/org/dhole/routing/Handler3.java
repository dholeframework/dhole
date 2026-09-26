package org.dhole.routing;

/**
 * A typed route handler with 3 parameter(s), usually a controller method reference registered with
 * {@link RouteBuilder#to}. Parameter values are bound from the request by the framework; this
 * interface only describes the Java function shape.
 */
@FunctionalInterface
public interface Handler3<A, B, C, R> {

    R handle(A a, B b, C c) throws Exception;
}
