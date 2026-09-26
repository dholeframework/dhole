package org.dhole.routing;

/**
 * A typed route handler with 1 parameter(s), usually a controller method reference registered with
 * {@link RouteBuilder#to}. Parameter values are bound from the request by the framework; this
 * interface only describes the Java function shape.
 */
@FunctionalInterface
public interface Handler1<A, R> {

    R handle(A a) throws Exception;
}
