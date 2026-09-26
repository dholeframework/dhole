package org.dhole.internal.di;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.dhole.di.DependencyException;
import org.junit.jupiter.api.Test;

class ResourceLifecycleTest {

    private final EventLog log = new EventLog();

    // Cleanup

    @Test
    void containerClosesOwnedResourcesInReverseCreationOrder() {
        DependencyContainer container = builder().component(Service.class).build();

        container.close();

        assertEquals(List.of("create Database", "create Repository", "create Service",
                "close Service", "close Repository", "close Database"), log.events);
    }

    @Test
    void lazilyCreatedSingletonsAreClosedToo() {
        DependencyContainer container = builder().build();
        container.resolve(Service.class);

        container.close();

        assertEquals(List.of("close Service", "close Repository", "close Database"), log.closes());
    }

    @Test
    void closingTwiceClosesOnce() {
        DependencyContainer container = builder().component(Database.class).build();

        container.close();
        container.close();

        assertEquals(List.of("close Database"), log.closes());
    }

    @Test
    void closedContainerRejectsResolution() {
        DependencyContainer container = builder().build();
        container.close();

        assertThrows(IllegalStateException.class, () -> container.resolve(Database.class));
    }

    @Test
    void externalInstancesAreNeverClosed() {
        External external = new External(log);
        DependencyContainer container = builder().bind(External.class).toInstance(external).build();

        container.close();

        assertEquals(List.of(), log.closes());
    }

    @Test
    void instancesWithTransferredOwnershipAreClosed() {
        External external = new External(log);
        DependencyContainer container = builder().bind(External.class).toOwnedInstance(external).build();

        container.close();

        assertEquals(List.of("close External"), log.closes());
    }

    @Test
    void factoryCreatedResourcesAreOwned() {
        DependencyContainer container = builder()
                .provide(Database.class, context -> new Database(context.resolve(EventLog.class)))
                .build();

        container.close();

        assertEquals(List.of("close Database"), log.closes());
    }

    @Test
    void prototypeResourcesBelongToTheirReceiver() {
        DependencyContainer container = builder().component(Database.class, ComponentScope.PROTOTYPE).build();
        Database first = container.resolve(Database.class);
        Database second = container.resolve(Database.class);

        container.close();

        assertNotSame(first, second);
        assertEquals(List.of(), log.closes());
    }

    @Test
    void everyResourceIsClosedAndFailuresAreReportedTogether() {
        DependencyContainer container = builder()
                .component(Database.class)
                .component(FailingClose.class)
                .component(OtherFailingClose.class)
                .build();

        DependencyException failure = assertThrows(DependencyException.class, container::close);

        assertEquals("Dependency Error\n\nFailed to close 2 component(s):\n- OtherFailingClose\n- FailingClose",
                failure.getMessage());
        assertEquals(2, failure.getSuppressed().length);
        assertTrue(log.closes().contains("close Database"));
    }

    // Rollback

    @Test
    void startupFailureClosesCreatedResourcesInReverseOrder() {
        DependencyException failure = assertThrows(DependencyException.class,
                () -> builder().component(Service.class).component(FailsOnConstruction.class).build());

        assertEquals(List.of("close Service", "close Repository", "close Database"), log.closes());
        assertTrue(failure.getMessage().startsWith("Dependency Error\n\nCould not construct FailsOnConstruction."),
                failure.getMessage());
        assertInstanceOf(IllegalStateException.class, failure.getCause());
    }

    @Test
    void startupFailureClosesInstancesWithTransferredOwnership() {
        External external = new External(log);

        assertThrows(DependencyException.class, () -> builder()
                .bind(External.class).toOwnedInstance(external)
                .component(FailsOnConstruction.class)
                .build());

        assertEquals(List.of("close External"), log.closes());
    }

    @Test
    void cleanupFailureDoesNotReplaceTheOriginalFailure() {
        DependencyException failure = assertThrows(DependencyException.class,
                () -> builder().component(FailingClose.class).component(FailsOnConstruction.class).build());

        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertEquals("constructor failure", failure.getCause().getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("close failure", failure.getSuppressed()[0].getMessage());
    }

    @Test
    void failedResolutionRollsBackItsPartialGraph() {
        DependencyContainer container = builder().build();

        assertThrows(DependencyException.class, () -> container.resolve(NeedsDatabaseThenFails.class));

        assertEquals(List.of("create Database", "close Database"), log.events);
    }

    @Test
    void failedResolutionDoesNotPublishOrOwnItsSingletons() {
        DependencyContainer container = builder().build();
        assertThrows(DependencyException.class, () -> container.resolve(NeedsDatabaseThenFails.class));

        Database database = container.resolve(Database.class);
        assertSame(database, container.resolve(Database.class));
        container.close();

        assertEquals(List.of("create Database", "close Database", "create Database", "close Database"), log.events);
    }

    @Test
    void failedResolutionClosesPrototypesItCreated() {
        DependencyContainer container = builder().component(Database.class, ComponentScope.PROTOTYPE).build();

        assertThrows(DependencyException.class, () -> container.resolve(NeedsDatabaseThenFails.class));

        assertEquals(List.of("close Database"), log.closes());
    }

    @Test
    void failedResolutionLeavesExistingSingletonsAlone() {
        DependencyContainer container = builder().component(Database.class).build();

        assertThrows(DependencyException.class, () -> container.resolve(NeedsDatabaseThenFails.class));

        assertEquals(List.of(), log.closes());
        container.close();
        assertEquals(List.of("close Database"), log.closes());
    }

    private ContainerBuilder builder() {
        return ContainerBuilder.create().bind(EventLog.class).toInstance(log);
    }

    public static final class EventLog {

        final List<String> events = new ArrayList<>();

        void record(String event) {
            events.add(event);
        }

        List<String> closes() {
            return events.stream().filter(event -> event.startsWith("close ")).toList();
        }
    }

    public static final class Database implements AutoCloseable {

        private final EventLog log;

        public Database(EventLog log) {
            this.log = log;
            log.record("create Database");
        }

        @Override
        public void close() {
            log.record("close Database");
        }
    }

    public static final class Repository implements AutoCloseable {

        private final EventLog log;

        public Repository(Database database, EventLog log) {
            this.log = log;
            log.record("create Repository");
        }

        @Override
        public void close() {
            log.record("close Repository");
        }
    }

    public static final class Service implements AutoCloseable {

        private final EventLog log;

        public Service(Repository repository, EventLog log) {
            this.log = log;
            log.record("create Service");
        }

        @Override
        public void close() {
            log.record("close Service");
        }
    }

    public static final class External implements AutoCloseable {

        private final EventLog log;

        public External(EventLog log) {
            this.log = log;
        }

        @Override
        public void close() {
            log.record("close External");
        }
    }

    public static final class FailsOnConstruction {

        public FailsOnConstruction() {
            throw new IllegalStateException("constructor failure");
        }
    }

    public static final class NeedsDatabaseThenFails {

        public NeedsDatabaseThenFails(Database database, FailsOnConstruction failing) {
        }
    }

    public static final class FailingClose implements AutoCloseable {

        public FailingClose() {
        }

        @Override
        public void close() {
            throw new IllegalStateException("close failure");
        }
    }

    public static final class OtherFailingClose implements AutoCloseable {

        public OtherFailingClose() {
        }

        @Override
        public void close() throws IOException {
            throw new IOException("other close failure");
        }
    }
}
