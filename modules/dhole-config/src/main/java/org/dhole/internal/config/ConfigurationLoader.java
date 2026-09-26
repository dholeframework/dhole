package org.dhole.internal.config;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;

import org.dhole.config.ConfigurationException;
import org.dhole.config.SettingsRegistry;

/**
 * Loads and validates the configuration of an application.
 *
 * <p>Runs the application's conventional {@code Settings.configure} (if any) with {@code Env}
 * bound to the given environment, then validates the declared settings.
 */
final class ConfigurationLoader {

    private ConfigurationLoader() {
    }

    /**
     * @throws ConfigurationException if any configuration is missing, malformed or invalid
     */
    static LoadedConfiguration load(Class<?> applicationClass, DefaultEnvironment environment) {
        Objects.requireNonNull(applicationClass, "applicationClass");
        Objects.requireNonNull(environment, "environment");

        Optional<Method> configure = SettingsLookup.find(applicationClass);
        EnvResolver resolver = new EnvResolver(environment);
        DefaultSettingsBuilder builder =
                new DefaultSettingsBuilder(environment, resolver, applicationClass.getSimpleName());
        configure.ifPresent(method -> resolver.within(() -> invoke(method, builder)));
        SettingsRegistry settings = builder.build();
        return new LoadedConfiguration(environment, settings, resolver.resolved());
    }

    private static Void invoke(Method configure, DefaultSettingsBuilder builder) {
        String settingsClass = configure.getDeclaringClass().getName();
        try {
            configure.invoke(null, builder);
            return null;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ConfigurationException configurationException) {
                throw configurationException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new ConfigurationException("Configuration Error\n\n" + settingsClass
                    + ".configure(SettingsBuilder) failed:\n" + cause.getClass().getName(), cause);
        } catch (IllegalAccessException e) {
            throw new ConfigurationException("Configuration Error\n\nUnable to call " + settingsClass
                    + ".configure(SettingsBuilder)", e);
        } catch (ExceptionInInitializerError e) {
            throw new ConfigurationException("Configuration Error\n\nUnable to initialize " + settingsClass,
                    e.getCause() == null ? e : e.getCause());
        }
    }
}
