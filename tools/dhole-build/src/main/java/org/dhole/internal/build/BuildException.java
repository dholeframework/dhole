package org.dhole.internal.build;

/**
 * A build problem the developer can act on. The message is complete and shown as is, without a
 * stack trace.
 */
public class BuildException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BuildException(String message) {
        super(message);
    }

    public BuildException(String message, Throwable cause) {
        super(message, cause);
    }
}
