package org.dhole.internal.module;

/**
 * A module failed to start. The message leads with the module's own developer-oriented message.
 */
public final class ModuleStartupException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String module;

    ModuleStartupException(String module, Throwable cause) {
        super(message(module, cause), cause);
        this.module = module;
    }

    public String module() {
        return module;
    }

    private static String message(String module, Throwable cause) {
        String detail = cause.getMessage() == null || cause.getMessage().isBlank()
                ? cause.getClass().getName()
                : cause.getMessage();
        return detail + "\n\n(module '" + module + "' failed to start)";
    }
}
