package org.dhole.internal.web;

/**
 * The request body's {@code Content-Type} is missing or has no serializer; answered with 415.
 */
final class UnsupportedMediaTypeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    UnsupportedMediaTypeException(String message) {
        super(message);
    }
}
