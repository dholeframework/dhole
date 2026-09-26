package org.dhole.internal.di;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.dhole.di.DependencyException;
import org.junit.jupiter.api.Test;

class RequestScopeTest {

    private final Log log = new Log();

    @Test
    void requestComponentIsReusedWithinOneRequest() {
        DependencyContainer container = container();

        try (RequestScope request = container.openRequestScope()) {
            RequestContext first = request.resolve(RequestContext.class);
            assertSame(first, request.resolve(RequestContext.class));
            assertSame(first, request.resolve(Handler.class).context);
        }
    }

    @Test
    void requestComponentIsNeverSharedBetweenRequests() {
        DependencyContainer container = container();

        RequestContext first;
        RequestContext second;
        try (RequestScope one = container.openRequestScope(); RequestScope two = container.openRequestScope()) {
            first = one.resolve(RequestContext.class);
            second = two.resolve(RequestContext.class);
        }

        assertNotSame(first, second);
    }

    @Test
    void singletonsAreSharedAcrossRequests() {
        DependencyContainer container = container();

        try (RequestScope one = container.openRequestScope(); RequestScope two = container.openRequestScope()) {
            assertSame(one.resolve(Handler.class).service, two.resolve(Handler.class).service);
        }
    }

    @Test
    void closingTheScopeClosesItsResourcesOnceInReverseOrder() {
        DependencyContainer container = container();
        RequestScope request = container.openRequestScope();
        request.resolve(Handler.class);

        request.close();
        request.close();

        assertEquals(List.of("close Handler", "close RequestContext"), closes());
        assertThrows(IllegalStateException.class, () -> request.resolve(RequestContext.class));
        container.close();
        assertEquals(List.of("close Handler", "close RequestContext", "close Service"), closes());
    }

    @Test
    void failedResolutionInsideARequestClosesWhatItCreatedAndPublishesNothing() {
        DependencyContainer container = ContainerBuilder.create()
                .bind(Log.class).toInstance(log)
                .component(RequestContext.class, ComponentScope.REQUEST)
                .component(Failing.class, ComponentScope.REQUEST)
                .build();

        try (RequestScope request = container.openRequestScope()) {
            DependencyException failure = assertThrows(DependencyException.class, () -> request.resolve(Failing.class));
            assertInstanceOf(IllegalStateException.class, failure.getCause());
            assertEquals(List.of("close RequestContext"), closes());

            RequestContext retried = request.resolve(RequestContext.class);
            assertSame(retried, request.resolve(RequestContext.class));
        }
        assertEquals(List.of("close RequestContext", "close RequestContext"), closes());
    }

    @Test
    void closeFailuresAreReportedAfterClosingEverything() {
        DependencyContainer container = ContainerBuilder.create()
                .bind(Log.class).toInstance(log)
                .component(RequestContext.class, ComponentScope.REQUEST)
                .component(FailingClose.class, ComponentScope.REQUEST)
                .build();
        RequestScope request = container.openRequestScope();
        request.resolve(RequestContext.class);
        request.resolve(FailingClose.class);

        DependencyException failure = assertThrows(DependencyException.class, request::close);

        assertEquals("Dependency Error\n\nFailed to close 1 component(s):\n- FailingClose", failure.getMessage());
        assertEquals(List.of("close RequestContext"), closes());
    }

    @Test
    void requestComponentOutsideARequestIsAScopeError() {
        DependencyException failure = assertThrows(DependencyException.class,
                () -> container().resolve(RequestContext.class));

        assertEquals("Scope Error\n\nRequestContext is request-scoped and can only be resolved inside a request.",
                failure.getMessage());
    }

    @Test
    void singletonDependingOnARequestComponentIsAScopeError() {
        DependencyException failure = assertThrows(DependencyException.class, () -> ContainerBuilder.create()
                .bind(Log.class).toInstance(log)
                .component(RequestContext.class, ComponentScope.REQUEST)
                .component(CaptiveSingleton.class)
                .build());

        assertEquals("Scope Error\n\nSingleton CaptiveSingleton depends on request-scoped RequestContext.\n\n"
                + "Dependency path:\nCaptiveSingleton\n  -> RequestContext", failure.getMessage());
    }

    @Test
    void singletonReachingARequestComponentThroughAPrototypeIsAScopeError() {
        DependencyException failure = assertThrows(DependencyException.class, () -> ContainerBuilder.create()
                .bind(Log.class).toInstance(log)
                .component(RequestContext.class, ComponentScope.REQUEST)
                .component(Handler.class, ComponentScope.PROTOTYPE)
                .component(IndirectCaptive.class)
                .build());

        assertTrue(failure.getMessage().endsWith(
                "Dependency path:\nIndirectCaptive\n  -> Handler\n      -> RequestContext"), failure.getMessage());
    }

    @Test
    void concurrentRequestsGetIsolatedInstances() throws Exception {
        DependencyContainer container = container();
        int requests = 16;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        try {
            List<Future<RequestContext>> results = new ArrayList<>();
            for (int index = 0; index < requests; index++) {
                Callable<RequestContext> task = () -> {
                    start.await();
                    try (RequestScope request = container.openRequestScope()) {
                        RequestContext context = request.resolve(Handler.class).context;
                        assertSame(context, request.resolve(RequestContext.class));
                        return context;
                    }
                };
                results.add(executor.submit(task));
            }
            start.countDown();
            List<RequestContext> contexts = new ArrayList<>();
            for (Future<RequestContext> result : results) {
                contexts.add(result.get());
            }
            assertEquals(requests, contexts.stream().distinct().count());
        } finally {
            executor.shutdownNow();
        }
        assertEquals(requests * 2L, closes().size());
    }

    private DependencyContainer container() {
        return ContainerBuilder.create()
                .bind(Log.class).toInstance(log)
                .component(RequestContext.class, ComponentScope.REQUEST)
                .component(Handler.class, ComponentScope.REQUEST)
                .build();
    }

    private List<String> closes() {
        return log.closes();
    }

    public static final class Log {

        private final List<String> events = Collections.synchronizedList(new ArrayList<>());

        void record(String event) {
            events.add(event);
        }

        List<String> closes() {
            synchronized (events) {
                return events.stream().filter(event -> event.startsWith("close ")).toList();
            }
        }
    }

    public static final class Service implements AutoCloseable {

        private final Log log;

        public Service(Log log) {
            this.log = log;
        }

        @Override
        public void close() {
            log.record("close Service");
        }
    }

    public static final class RequestContext implements AutoCloseable {

        private final Log log;

        public RequestContext(Log log) {
            this.log = log;
        }

        @Override
        public void close() {
            log.record("close RequestContext");
        }
    }

    public static final class Handler implements AutoCloseable {

        final RequestContext context;
        final Service service;
        private final Log log;

        public Handler(RequestContext context, Service service, Log log) {
            this.context = context;
            this.service = service;
            this.log = log;
        }

        @Override
        public void close() {
            log.record("close Handler");
        }
    }

    public static final class Failing {

        public Failing(RequestContext context) {
            throw new IllegalStateException("constructor failure");
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

    public static final class CaptiveSingleton {

        public CaptiveSingleton(RequestContext context) {
        }
    }

    public static final class IndirectCaptive {

        public IndirectCaptive(Handler handler) {
        }
    }
}
