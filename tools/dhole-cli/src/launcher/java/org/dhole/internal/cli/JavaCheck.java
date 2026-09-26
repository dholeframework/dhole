package org.dhole.internal.cli;

import java.lang.reflect.InvocationTargetException;

import javax.tools.ToolProvider;

/**
 * First class the {@code dhole} scripts start. Compiled for Java 8 so that an old Java can still
 * explain the problem instead of failing with an unsupported class version; it checks for a JDK 21
 * or newer with {@code javac}, then hands over to the command line.
 */
public final class JavaCheck {

    private static final int REQUIRED = 21;

    private JavaCheck() {
    }

    public static void main(String[] args) throws Throwable {
        String specification = System.getProperty("java.specification.version");
        int feature = specification.startsWith("1.")
                ? Integer.parseInt(specification.substring(2))
                : Integer.parseInt(specification.split("\\.")[0]);
        if (feature < REQUIRED) {
            System.err.println("Dhole requires a JDK " + REQUIRED + " or newer, but this is Java "
                    + System.getProperty("java.version") + " (" + System.getProperty("java.home") + ").");
            System.err.println("Install a JDK " + REQUIRED + "+ and set JAVA_HOME.");
            System.exit(1);
        }
        if (ToolProvider.getSystemJavaCompiler() == null) {
            System.err.println("Dhole requires a JDK, but " + System.getProperty("java.home")
                    + " has no Java compiler (javac); it is a JRE.");
            System.err.println("Install a JDK " + REQUIRED + "+ and set JAVA_HOME.");
            System.exit(1);
        }
        try {
            Class.forName("org.dhole.internal.cli.Main").getMethod("main", String[].class).invoke(null, (Object) args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
