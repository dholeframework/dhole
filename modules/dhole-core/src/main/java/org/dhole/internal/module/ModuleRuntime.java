package org.dhole.internal.module;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The activated modules of one application, in activation order. Starts them in that order and
 * rolls back the started ones when one fails; stops them in reverse order.
 */
public final class ModuleRuntime {

    private final List<Activation> activations;
    private final List<Activation> started = new ArrayList<>();

    private ModuleRuntime(List<Activation> activations) {
        this.activations = List.copyOf(activations);
    }

    public static ModuleRuntime none() {
        return new ModuleRuntime(List.of());
    }

    /**
     * Validates the index and creates the activator of every module that declares one, through the
     * application's class loader.
     *
     * @throws IllegalStateException if the module graph is invalid or an activator is unusable
     */
    public static ModuleRuntime of(ModuleIndex index, ClassLoader loader) {
        List<Activation> activations = new ArrayList<>();
        for (ModuleDescriptor module : index.activationOrder()) {
            if (module.activator().isPresent()) {
                activations.add(new Activation(module.id(), create(module, loader)));
            }
        }
        return new ModuleRuntime(activations);
    }

    /**
     * Test and composition seam: activators already created, in activation order.
     */
    public static ModuleRuntime of(List<Activation> activations) {
        return new ModuleRuntime(activations);
    }

    /**
     * Starts every module in order. When one fails, the modules already started are stopped in
     * reverse order and the failure is rethrown with their stop failures suppressed.
     *
     * @throws ModuleStartupException if a module fails to start
     */
    public void start(ActivationContext context) {
        for (Activation activation : activations) {
            try {
                activation.activator().start(context);
            } catch (Exception | LinkageError e) {
                ModuleStartupException failure = new ModuleStartupException(activation.id(), e);
                stopStarted(failure);
                throw failure;
            }
            started.add(activation);
        }
    }

    /**
     * Stops the started modules in reverse order; every stop is attempted.
     *
     * @throws IllegalStateException carrying every stop failure
     */
    public void stop() {
        IllegalStateException failure = new IllegalStateException("Module Error\n\nOne or more modules failed to stop.");
        if (stopStarted(failure)) {
            throw failure;
        }
    }

    public List<String> ids() {
        return activations.stream().map(Activation::id).toList();
    }

    private boolean stopStarted(RuntimeException failure) {
        boolean failed = false;
        for (int index = started.size() - 1; index >= 0; index--) {
            Activation activation = started.get(index);
            try {
                activation.activator().stop();
            } catch (Exception e) {
                failure.addSuppressed(e);
                failed = true;
            }
        }
        started.clear();
        return failed;
    }

    private static ModuleActivator create(ModuleDescriptor module, ClassLoader loader) {
        String name = module.activator().orElseThrow();
        Class<?> type;
        try {
            type = Class.forName(name, false, loader);
        } catch (ClassNotFoundException e) {
            throw unusable(module, "its activator " + name + " does not exist", e);
        }
        if (!ModuleActivator.class.isAssignableFrom(type) || Modifier.isAbstract(type.getModifiers())) {
            throw unusable(module, name + " is not a concrete ModuleActivator", null);
        }
        try {
            Constructor<?> constructor = type.getConstructor();
            return (ModuleActivator) constructor.newInstance();
        } catch (NoSuchMethodException e) {
            throw unusable(module, name + " has no public no-argument constructor", e);
        } catch (InvocationTargetException e) {
            throw unusable(module, name + " could not be created", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw unusable(module, name + " could not be created", e);
        }
    }

    private static IllegalStateException unusable(ModuleDescriptor module, String problem, Throwable cause) {
        return new IllegalStateException("Module Error\n\nModule '" + module.id() + "' cannot be activated: "
                + problem + ".\n\nRebuild the application.", cause);
    }

    /**
     * One activator with its module ID.
     */
    public record Activation(String id, ModuleActivator activator) {

        public Activation {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(activator, "activator");
        }
    }
}
