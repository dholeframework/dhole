package org.dhole.internal.web;

/**
 * The client accepts none of the media types the response can be written as; answered with 406.
 */
final class NotAcceptableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    NotAcceptableException() {
        super("Not acceptable", null, false, false);
    }
}
