package org.dhole.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DefaultApplicationTest {

    @Test
    void newApplicationIsCreated() {
        DefaultApplication application = newApplication();

        assertEquals(ApplicationState.CREATED, application.state());
    }

    @Test
    void startMovesApplicationToRunning() {
        DefaultApplication application = newApplication();

        application.start();

        assertEquals(ApplicationState.RUNNING, application.state());
    }

    @Test
    void stopAfterStartMovesApplicationToStopped() {
        DefaultApplication application = newApplication();
        application.start();

        application.stop();

        assertEquals(ApplicationState.STOPPED, application.state());
    }

    @Test
    void applicationCannotStartTwice() {
        DefaultApplication application = newApplication();
        application.start();

        assertThrows(IllegalStateException.class, application::start);
        assertEquals(ApplicationState.RUNNING, application.state());
    }

    @Test
    void applicationCannotStopTwice() {
        DefaultApplication application = newApplication();
        application.start();
        application.stop();

        assertThrows(IllegalStateException.class, application::stop);
        assertEquals(ApplicationState.STOPPED, application.state());
    }

    @Test
    void stopBeforeStartIsRejected() {
        DefaultApplication application = newApplication();

        assertThrows(IllegalStateException.class, application::stop);
        assertEquals(ApplicationState.CREATED, application.state());
    }

    @Test
    void startAfterStopIsRejected() {
        DefaultApplication application = newApplication();
        application.start();
        application.stop();

        assertThrows(IllegalStateException.class, application::start);
        assertEquals(ApplicationState.STOPPED, application.state());
    }

    @Test
    void contextIsTheOneOwnedByTheApplicationInEveryState() {
        ApplicationContext context = new DefaultApplicationContext();
        DefaultApplication application = new DefaultApplication(context);

        assertSame(context, application.context());
        application.start();
        assertSame(context, application.context());
        application.stop();
        assertSame(context, application.context());
    }

    @Test
    void applicationRequiresAContext() {
        assertThrows(NullPointerException.class, () -> new DefaultApplication(null));
    }

    private static DefaultApplication newApplication() {
        return new DefaultApplication(new DefaultApplicationContext());
    }
}
