package org.dhole.internal.web;

import org.dhole.config.Environment;
import org.dhole.internal.config.Configuration;
import org.dhole.internal.module.ActivationContext;
import org.dhole.internal.module.ModuleActivator;

/**
 * Activates the web module for {@code Dhole.run} (MODULE_SYSTEM.md §29): loads the application's
 * configuration (M2 Settings/Environment), then starts the existing {@link WebRuntime} on the
 * configured port. Development and test bind the loopback interface; production binds every
 * interface. Unexpected errors reveal details only in development.
 */
public final class WebActivator implements ModuleActivator {

    private WebRuntime runtime;

    public WebActivator() {
    }

    @Override
    public void start(ActivationContext context) {
        Configuration configuration = Configuration.load(context.applicationClass());
        Environment environment = configuration.environment();
        String host = environment.isProduction() ? "0.0.0.0" : "127.0.0.1";
        RuntimeMode mode = environment.isDevelopment() ? RuntimeMode.DEVELOPMENT : RuntimeMode.PRODUCTION;
        runtime = WebRuntime.start(context.classLoader(), host, configuration.app().port(), mode);
        context.output().println("Server http://localhost:" + runtime.port() + " (" + environment.name() + ")");
    }

    @Override
    public void stop() {
        if (runtime != null) {
            WebRuntime running = runtime;
            runtime = null;
            running.close();
        }
    }
}
