package org.dhole.internal.config;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Optional;

import org.dhole.config.ConfigurationException;
import org.dhole.config.SettingsBuilder;

/**
 * Finds the conventional {@code Settings} class of an application.
 *
 * <p>For an application class {@code com.example.shop.App} it loads exactly
 * {@code com.example.shop.config.Settings} with the application class's class loader. There is no
 * scanning: no other package or class is considered. The class must declare
 * {@code public static void configure(SettingsBuilder settings)}.
 */
final class SettingsLookup {

    static final String METHOD = "configure";

    private SettingsLookup() {
    }

    /**
     * Returns the configure method, or empty when the application has no Settings class.
     *
     * @throws ConfigurationException if the Settings class exists but violates the convention
     */
    static Optional<Method> find(Class<?> applicationClass) {
        String className = settingsClassName(applicationClass);
        Class<?> settings;
        try {
            settings = Class.forName(className, false, applicationClass.getClassLoader());
        } catch (ClassNotFoundException e) {
            return Optional.empty();
        } catch (LinkageError e) {
            throw new ConfigurationException("Configuration Error\n\nUnable to load " + className, e);
        }
        return Optional.of(configureMethod(settings));
    }

    static String settingsClassName(Class<?> applicationClass) {
        String root = applicationClass.getPackageName();
        return root.isEmpty() ? "config.Settings" : root + ".config.Settings";
    }

    private static Method configureMethod(Class<?> settings) {
        if (!Modifier.isPublic(settings.getModifiers())) {
            throw invalid(settings, "the class is not public");
        }
        Method[] candidates = Arrays.stream(settings.getDeclaredMethods())
                .filter(method -> method.getName().equals(METHOD))
                .toArray(Method[]::new);
        if (candidates.length == 0) {
            throw invalid(settings, "the method is missing");
        }
        Method method = Arrays.stream(candidates)
                .filter(candidate -> Arrays.equals(candidate.getParameterTypes(),
                        new Class<?>[] {SettingsBuilder.class}))
                .findFirst()
                .orElseThrow(() -> invalid(settings, "the method has the wrong parameters"));
        if (!Modifier.isPublic(method.getModifiers())) {
            throw invalid(settings, "the method is not public");
        }
        if (!Modifier.isStatic(method.getModifiers())) {
            throw invalid(settings, "the method is not static");
        }
        if (method.getReturnType() != void.class) {
            throw invalid(settings, "the method does not return void");
        }
        return method;
    }

    private static ConfigurationException invalid(Class<?> settings, String reason) {
        return new ConfigurationException("Configuration Error\n\n" + settings.getName()
                + " must declare:\npublic static void configure(SettingsBuilder settings)\n\nProblem:\n" + reason);
    }
}
