package org.dhole.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DefaultApplicationTest {

    @Test
    void newApplicationIsCreated() {
        DefaultApplication application = new DefaultApplication();

        assertEquals(ApplicationState.CREATED, application.state());
    }

    @Test
    void startMovesApplicationToRunning() {
        DefaultApplication application = new DefaultApplication();

        application.start();

        assertEquals(ApplicationState.RUNNING, application.state());
    }

    @Test
    void stopAfterStartMovesApplicationToStopped() {
        DefaultApplication application = new DefaultApplication();
        application.start();

        application.stop();

        assertEquals(ApplicationState.STOPPED, application.state());
    }

    @Test
    void applicationCannotStartTwice() {
        DefaultApplication application = new DefaultApplication();
        application.start();

        assertThrows(IllegalStateException.class, application::start);
        assertEquals(ApplicationState.RUNNING, application.state());
    }

    @Test
    void applicationCannotStopTwice() {
        DefaultApplication application = new DefaultApplication();
        application.start();
        application.stop();

        assertThrows(IllegalStateException.class, application::stop);
        assertEquals(ApplicationState.STOPPED, application.state());
    }

    @Test
    void stopBeforeStartIsRejected() {
        DefaultApplication application = new DefaultApplication();

        assertThrows(IllegalStateException.class, application::stop);
        assertEquals(ApplicationState.CREATED, application.state());
    }

    @Test
    void startAfterStopIsRejected() {
        DefaultApplication application = new DefaultApplication();
        application.start();
        application.stop();

        assertThrows(IllegalStateException.class, application::start);
        assertEquals(ApplicationState.STOPPED, application.state());
    }
}
