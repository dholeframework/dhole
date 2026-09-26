package org.dhole.internal.devtools;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Runs the application in-process for {@code dhole dev} (CORE_ARCHITECTURE.md §45, §46):
 *
 * <pre>
 * runtime class loader     locked Dhole and third-party JARs (reused while they do not change)
 *   application loader     compiled application classes and metadata (new for every start)
 * </pre>
 *
 * The application starts through the core {@code Launcher}, the same bootstrap as
 * {@code Dhole.run}, so modules are activated from the new {@code modules.idx}. Stopping closes the
 * application (modules in reverse order) and discards its class loader; nothing is mutated in place.
 */
final class ApplicationRuntime implements AutoCloseable {

    private static final String LAUNCHER = "org.dhole.internal.bootstrap.Launcher";

    private final PrintStream output;
    private List<Path> runtimeArtifacts = List.of();
    private URLClassLoader runtimeLoader;
    private URLClassLoader applicationLoader;
    private AutoCloseable application;

    ApplicationRuntime(PrintStream output) {
        this.output = Objects.requireNonNull(output, "output");
    }

    boolean running() {
        return application != null;
    }

    /**
     * Starts the application from its compiled classes.
     *
     * @throws StartupFailure if the application fails to start; nothing is left running
     */
    void start(List<Path> runtime, Path classes, String mainClass) {
        if (application != null) {
            throw new IllegalStateException("The application is already running");
        }
        if (!runtime.equals(runtimeArtifacts) || runtimeLoader == null) {
            closeRuntimeLoader();
            runtimeLoader = new URLClassLoader("dhole-runtime", urls(runtime), ClassLoader.getPlatformClassLoader());
            runtimeArtifacts = List.copyOf(runtime);
        }
        applicationLoader = new URLClassLoader("dhole-application", urls(List.of(classes)), runtimeLoader);
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(applicationLoader);
        try {
            Class<?> applicationClass = Class.forName(mainClass, false, applicationLoader);
            Method start = runtimeLoader.loadClass(LAUNCHER).getMethod("start", Class.class, PrintStream.class);
            application = (AutoCloseable) start.invoke(null, applicationClass, output);
        } catch (InvocationTargetException e) {
            discardApplicationLoader();
            throw new StartupFailure(e.getCause());
        } catch (ReflectiveOperationException | LinkageError e) {
            discardApplicationLoader();
            throw new StartupFailure(e);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    /**
     * Stops the running application, if any, and discards its class loader.
     */
    void stop() {
        if (application == null) {
            return;
        }
        AutoCloseable running = application;
        application = null;
        try {
            running.close();
        } catch (Exception e) {
            output.println("The application did not stop cleanly: " + e.getMessage());
        } finally {
            discardApplicationLoader();
        }
    }

    @Override
    public void close() {
        stop();
        closeRuntimeLoader();
    }

    private void discardApplicationLoader() {
        if (applicationLoader != null) {
            try {
                applicationLoader.close();
            } catch (IOException ignored) {
                // Closing only releases file handles; failure leaves them to the garbage collector.
            }
            applicationLoader = null;
        }
    }

    private void closeRuntimeLoader() {
        if (runtimeLoader != null) {
            try {
                runtimeLoader.close();
            } catch (IOException ignored) {
                // See discardApplicationLoader.
            }
            runtimeLoader = null;
        }
    }

    private static URL[] urls(List<Path> paths) {
        List<URL> urls = new ArrayList<>();
        for (Path path : paths) {
            try {
                urls.add(path.toUri().toURL());
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException(path.toString(), e);
            }
        }
        return urls.toArray(URL[]::new);
    }

    /**
     * The application failed to start; the cause carries the developer-oriented message.
     */
    static final class StartupFailure extends RuntimeException {

        private static final long serialVersionUID = 1L;

        StartupFailure(Throwable cause) {
            super(cause.getMessage() == null ? cause.getClass().getName() : cause.getMessage(), cause);
        }
    }
}
