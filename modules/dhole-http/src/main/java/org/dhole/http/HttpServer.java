package org.dhole.http;

/**
 * Server SPI: accepts connections and passes every request to one {@link HttpHandler}.
 * Implementations are replaceable without changing application code.
 */
public interface HttpServer {

    /**
     * Starts accepting requests.
     *
     * @throws IllegalStateException if the server was already started
     */
    void start(HttpHandler handler);

    /**
     * Stops accepting requests and releases every resource the server owns. Does nothing if the
     * server is not running.
     */
    void stop();
}
