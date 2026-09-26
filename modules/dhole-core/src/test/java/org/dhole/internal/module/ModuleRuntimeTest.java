package org.dhole.internal.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.dhole.application.ApplicationState;
import org.dhole.internal.lifecycle.LifecycleManager;
import org.junit.jupiter.api.Test;

class ModuleRuntimeTest {

    static final List<String> EVENTS = Collections.synchronizedList(new ArrayList<>());

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final ActivationContext context =
            new ActivationContext(ModuleRuntimeTest.class, new PrintStream(output, true, StandardCharsets.UTF_8));

    @Test
    void modulesStartInOrderAndStopInReverse() {
        List<String> events = new ArrayList<>();
        ModuleRuntime modules = ModuleRuntime.of(List.of(recording("a", events, false), recording("b", events, false)));

        modules.start(context);
        modules.stop();

        assertEquals(List.of("start a", "start b", "stop b", "stop a"), events);
    }

    @Test
    void failingModuleRollsBackTheStartedOnesAndTheApplicationFails() {
        List<String> events = new ArrayList<>();
        ModuleRuntime modules = ModuleRuntime.of(List.of(recording("a", events, false), recording("b", events, false),
                recording("c", events, true), recording("d", events, false)));
        LifecycleManager lifecycle = new LifecycleManager(context.output(), modules, context);

        ModuleStartupException failure = assertThrows(ModuleStartupException.class, lifecycle::start);

        assertEquals(List.of("start a", "start b", "start c", "stop b", "stop a"), events);
        assertEquals(ApplicationState.FAILED, lifecycle.state());
        assertEquals("c", failure.module());
        assertEquals("c cannot start\n\n(module 'c' failed to start)", failure.getMessage());
        assertEquals(List.of("Application starting...", "Application failed to start."),
                output.toString(StandardCharsets.UTF_8).lines().toList());
        assertThrows(IllegalStateException.class, lifecycle::start);
    }

    @Test
    void shutdownAfterPartialStartupStopsEachStartedModuleOnce() {
        List<String> events = new ArrayList<>();
        ModuleRuntime modules = ModuleRuntime.of(List.of(recording("a", events, false), recording("b", events, true)));
        LifecycleManager lifecycle = new LifecycleManager(context.output(), modules, context);

        assertThrows(ModuleStartupException.class, lifecycle::start);
        lifecycle.stopIfRunning();

        assertEquals(List.of("start a", "start b", "stop a"), events);
        assertEquals(ApplicationState.FAILED, lifecycle.state());
    }

    @Test
    void stopFailuresAreReportedAfterEveryModuleStopped() {
        List<String> events = new ArrayList<>();
        ModuleActivator failingStop = new ModuleActivator() {
            @Override
            public void start(ActivationContext ignored) {
                events.add("start a");
            }

            @Override
            public void stop() {
                throw new IllegalStateException("a cannot stop");
            }
        };
        ModuleRuntime modules = ModuleRuntime.of(List.of(new ModuleRuntime.Activation("a", failingStop),
                recording("b", events, false)));
        LifecycleManager lifecycle = new LifecycleManager(context.output(), modules, context);
        lifecycle.start();

        IllegalStateException failure = assertThrows(IllegalStateException.class, lifecycle::stop);

        assertEquals(List.of("start a", "start b", "stop b"), events);
        assertEquals("a cannot stop", failure.getSuppressed()[0].getMessage());
        assertEquals(ApplicationState.STOPPED, lifecycle.state());
    }

    @Test
    void activatorsAreCreatedFromTheIndexThroughTheApplicationLoader() {
        EVENTS.clear();
        ModuleIndex index = ModuleIndex.parse("""
                dhole-modules 1

                module first
                activator org.dhole.internal.module.ModuleRuntimeTest$FirstActivator

                module passive

                module second
                activator org.dhole.internal.module.ModuleRuntimeTest$SecondActivator
                requires first passive
                """);

        ModuleRuntime modules = ModuleRuntime.of(index, getClass().getClassLoader());
        modules.start(context);
        modules.stop();

        assertEquals(List.of("first", "second"), modules.ids());
        assertEquals(List.of("start first", "start second " + ModuleRuntimeTest.class.getSimpleName(), "stop second",
                "stop first"), EVENTS);
    }

    @Test
    void unusableActivatorsFailBeforeAnythingStarts() {
        for (String activator : List.of("com.example.Missing", "java.lang.String",
                "org.dhole.internal.module.ModuleRuntimeTest$NoDefaultConstructor",
                "org.dhole.internal.module.ModuleRuntimeTest$FailingConstructor")) {
            ModuleIndex index = ModuleIndex.parse("dhole-modules 1\n\nmodule web\nactivator " + activator + "\n");
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> ModuleRuntime.of(index, getClass().getClassLoader()), activator);
            assertTrue(failure.getMessage().startsWith("Module Error\n\nModule 'web' cannot be activated: "),
                    failure.getMessage());
        }
    }

    @Test
    void contextExposesTheApplicationLoader() {
        assertSame(ModuleRuntimeTest.class.getClassLoader(), context.classLoader());
    }

    private static ModuleRuntime.Activation recording(String id, List<String> events, boolean failStart) {
        return new ModuleRuntime.Activation(id, new ModuleActivator() {
            @Override
            public void start(ActivationContext ignored) {
                events.add("start " + id);
                if (failStart) {
                    throw new IllegalStateException(id + " cannot start");
                }
            }

            @Override
            public void stop() {
                events.add("stop " + id);
            }
        });
    }

    public static final class FirstActivator implements ModuleActivator {

        @Override
        public void start(ActivationContext context) {
            EVENTS.add("start first");
        }

        @Override
        public void stop() {
            EVENTS.add("stop first");
        }
    }

    public static final class SecondActivator implements ModuleActivator {

        @Override
        public void start(ActivationContext context) {
            EVENTS.add("start second " + context.applicationClass().getSimpleName());
        }

        @Override
        public void stop() {
            EVENTS.add("stop second");
        }
    }

    public static final class NoDefaultConstructor implements ModuleActivator {

        public NoDefaultConstructor(String ignored) {
        }

        @Override
        public void start(ActivationContext context) {
        }

        @Override
        public void stop() {
        }
    }

    public static final class FailingConstructor implements ModuleActivator {

        public FailingConstructor() {
            throw new IllegalStateException("boom");
        }

        @Override
        public void start(ActivationContext context) {
        }

        @Override
        public void stop() {
        }
    }
}
