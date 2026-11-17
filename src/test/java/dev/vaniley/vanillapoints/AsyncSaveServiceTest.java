package dev.vaniley.vanillapoints;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@Timeout(10)
class AsyncSaveServiceTest {
    @Test
    void coalescesRequestsWithoutTakingSnapshotsOnCommandThreads() {
        RecordingStorage storage = new RecordingStorage();
        QueuedExecutor executor = new QueuedExecutor();
        AsyncSaveService service = service(storage, executor);

        for (int i = 0; i < 1_000; i++) {
            storage.setWarp("warp" + i, point(i));
            service.requestSave(null);
        }

        assertEquals(0, storage.snapshots.get());
        assertEquals(1, executor.tasks.size());
        executor.runNext();
        assertEquals(1, storage.snapshots.get());
        assertEquals(1, storage.saved.size());
        assertEquals(1_000, storage.saved.getFirst().warps().size());
        assertTrue(service.flush(0));
    }

    @Test
    void mutationDuringWriteIsSavedAfterTheOlderSnapshot() throws Exception {
        RecordingStorage storage = new RecordingStorage();
        QueuedExecutor executor = new QueuedExecutor();
        AsyncSaveService service = service(storage, executor);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        storage.beforeSave = blockOnce(entered, release);
        storage.setWarp("shop", point(1));
        service.requestSave(null);

        CompletableFuture<Void> worker = CompletableFuture.runAsync(executor::runNext);
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            storage.setWarp("shop", point(2));
            service.requestSave(null);
        } finally {
            release.countDown();
        }
        worker.get(5, TimeUnit.SECONDS);

        assertEquals(List.of(point(1), point(2)), storage.saved.stream().map(s -> s.warps().get("shop")).toList());
        assertEquals(1, storage.maximumWriters.get());
        assertTrue(service.flush(0));
    }

    @Test
    void synchronousSaveWaitsForWriterAndDoesNotOverwriteWithAnOlderSnapshot() throws Exception {
        RecordingStorage storage = new RecordingStorage();
        QueuedExecutor executor = new QueuedExecutor();
        AsyncSaveService service = service(storage, executor);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        storage.beforeSave = blockOnce(entered, release);
        storage.setSpawn(point(1));
        service.requestSave(null);
        CompletableFuture<Void> worker = CompletableFuture.runAsync(executor::runNext);
        CompletableFuture<Boolean> synchronous = null;
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            storage.setSpawn(point(2));
            synchronous = CompletableFuture.supplyAsync(service::saveNow);
            assertFalse(service.flush(0));
        } finally {
            release.countDown();
        }
        worker.get(5, TimeUnit.SECONDS);
        assertTrue(synchronous.get(5, TimeUnit.SECONDS));
        assertEquals(point(2), storage.saved.getLast().spawn());
        assertEquals(1, storage.maximumWriters.get());
    }

    @Test
    void flushStealsQueuedWorkAndInvalidatesTheStaleCallback() {
        RecordingStorage storage = new RecordingStorage();
        QueuedExecutor executor = new QueuedExecutor();
        AsyncSaveService service = service(storage, executor);
        storage.setSpawn(point(1));
        service.requestSave(null);
        assertTrue(service.flush(0));
        storage.setSpawn(point(2));
        service.requestSave(null);

        executor.runNext();
        assertEquals(1, storage.saved.size());
        executor.runNext();
        assertEquals(List.of(point(1), point(2)), storage.saved.stream().map(PointStorageSnapshot::spawn).toList());
    }

    @Test
    void failedWriteStaysPendingWithoutAnImmediateRetryLoop() {
        RecordingStorage storage = new RecordingStorage();
        QueuedExecutor executor = new QueuedExecutor();
        AsyncSaveService service = service(storage, executor);
        storage.setSpawn(point(1));
        storage.failNext.set(true);
        service.requestSave(null);
        executor.runNext();

        assertEquals(1, storage.attempts.get());
        assertTrue(executor.tasks.isEmpty());
        assertTrue(storage.saved.isEmpty());
        assertTrue(service.flush(0));
        assertEquals(point(1), storage.saved.getLast().spawn());
        assertEquals(2, storage.attempts.get());
    }

    @Test
    void failedSynchronousSaveStaysPendingForFlush() {
        RecordingStorage storage = new RecordingStorage();
        AsyncSaveService service = service(storage, new QueuedExecutor());
        storage.setSpawn(point(1));
        storage.failNext.set(true);

        assertFalse(service.saveNow());
        assertTrue(service.flush(0));
        assertEquals(point(1), storage.saved.getLast().spawn());
    }

    @Test
    void requestArrivingDuringAFailedWriteIsNotStranded() throws Exception {
        RecordingStorage storage = new RecordingStorage();
        QueuedExecutor executor = new QueuedExecutor();
        AsyncSaveService service = service(storage, executor);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        storage.beforeSave = blockOnce(entered, release);
        storage.failNext.set(true);
        storage.setSpawn(point(1));
        service.requestSave(null);
        CompletableFuture<Void> worker = CompletableFuture.runAsync(executor::runNext);
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            storage.setSpawn(point(2));
            service.requestSave(null);
        } finally {
            release.countDown();
        }
        worker.get(5, TimeUnit.SECONDS);

        assertEquals(2, storage.attempts.get());
        assertEquals(point(2), storage.saved.getLast().spawn());
        assertTrue(service.flush(0));
    }

    @Test
    void rejectedSchedulerDoesNotLoseTheSave() {
        RecordingStorage storage = new RecordingStorage();
        AsyncSaveService service = service(storage, task -> {
            throw new RejectedExecutionException("disabled scheduler");
        });
        storage.setSpawn(point(1));
        service.requestSave(null);

        assertEquals(0, storage.snapshots.get());
        assertTrue(service.flush(0));
        assertEquals(point(1), storage.saved.getLast().spawn());
    }

    @Test
    void shutdownSavesDeferredMutationsAndMakesQueuedCallbacksHarmless() {
        RecordingStorage storage = new RecordingStorage();
        QueuedExecutor executor = new QueuedExecutor();
        AsyncSaveService service = service(storage, executor);
        storage.setSpawn(point(1));
        service.requestSave(null);
        storage.setSpawn(point(2));

        assertTrue(service.shutdown(0));
        executor.runNext();
        service.requestSave(null);
        assertFalse(service.saveNow());
        assertTrue(service.shutdown(0));
        assertTrue(executor.tasks.isEmpty());
        assertEquals(1, storage.saved.size());
        assertEquals(point(2), storage.saved.getLast().spawn());
    }

    @Test
    void shutdownWithNoRequestsStillSavesTheCurrentStorage() {
        RecordingStorage storage = new RecordingStorage();
        AsyncSaveService service = service(storage, new QueuedExecutor());
        storage.setSpawn(point(3));

        assertTrue(service.shutdown(0));
        assertEquals(point(3), storage.saved.getLast().spawn());
    }

    @Test
    void failedShutdownCanBeRetriedWithoutLosingTheFinalSnapshot() {
        RecordingStorage storage = new RecordingStorage();
        AsyncSaveService service = service(storage, new QueuedExecutor());
        storage.setSpawn(point(4));
        storage.failNext.set(true);

        assertFalse(service.shutdown(0));
        assertTrue(service.shutdown(0));
        assertEquals(point(4), storage.saved.getLast().spawn());
    }

    @Test
    void retiresCleanServiceWithoutAnotherWrite() {
        RecordingStorage storage = new RecordingStorage();
        QueuedExecutor executor = new QueuedExecutor();
        AsyncSaveService service = service(storage, executor);
        service.requestSave(null);
        assertTrue(service.saveNow());
        assertTrue(service.retireIfClean());
        executor.runNext();
        service.requestSave(null);

        assertFalse(service.saveNow());
        assertTrue(service.retireIfClean());
        assertEquals(1, storage.attempts.get());
        assertTrue(executor.tasks.isEmpty());
    }

    @Test
    void dirtyServiceCannotRetireWithoutSaving() {
        RecordingStorage storage = new RecordingStorage();
        QueuedExecutor executor = new QueuedExecutor();
        AsyncSaveService service = service(storage, executor);
        service.requestSave(null);

        assertFalse(service.retireIfClean());
        executor.runNext();
        assertTrue(service.retireIfClean());
        assertEquals(1, storage.saved.size());
    }

    @Test
    void timedOutShutdownDoesNotStartAConcurrentWriterAndCanBeRetried() throws Exception {
        RecordingStorage storage = new RecordingStorage();
        QueuedExecutor executor = new QueuedExecutor();
        AsyncSaveService service = service(storage, executor);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        storage.beforeSave = blockOnce(entered, release);
        storage.setSpawn(point(1));
        service.requestSave(null);
        CompletableFuture<Void> worker = CompletableFuture.runAsync(executor::runNext);
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            storage.setSpawn(point(2));
            assertFalse(service.retireIfClean());
            assertFalse(service.shutdown(0));
            assertEquals(1, storage.attempts.get());
        } finally {
            release.countDown();
        }
        worker.get(5, TimeUnit.SECONDS);
        assertTrue(service.shutdown(0));
        assertEquals(point(2), storage.saved.getLast().spawn());
        assertEquals(1, storage.maximumWriters.get());
    }

    @Test
    void interruptedFlushPreservesInterruptionAndPendingData() throws Exception {
        RecordingStorage storage = new RecordingStorage();
        QueuedExecutor executor = new QueuedExecutor();
        AsyncSaveService service = service(storage, executor);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        storage.beforeSave = blockOnce(entered, release);
        service.requestSave(null);
        CompletableFuture<Void> worker = CompletableFuture.runAsync(executor::runNext);
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            Thread.currentThread().interrupt();
            assertFalse(service.flush(1_000));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
            release.countDown();
        }
        worker.get(5, TimeUnit.SECONDS);
        assertTrue(service.flush(0));
    }

    @Test
    void notificationFailureDoesNotPreventRetryOrRelease() {
        RecordingStorage storage = new RecordingStorage();
        QueuedExecutor executor = new QueuedExecutor();
        AtomicInteger notifications = new AtomicInteger();
        AsyncSaveService service = new AsyncSaveService(storage, logger(), executor, player -> {
            notifications.incrementAndGet();
            throw new RejectedExecutionException("player retired");
        });
        storage.failNext.set(true);
        service.requestSave(mock(Player.class));
        executor.runNext();

        assertEquals(1, notifications.get());
        assertTrue(service.flush(0));
        assertEquals(1, storage.saved.size());
    }

    @Test
    void unexpectedStorageExceptionStillRetainsDirtyState() {
        RecordingStorage storage = new RecordingStorage();
        QueuedExecutor executor = new QueuedExecutor();
        AsyncSaveService service = service(storage, executor);
        storage.beforeSave = snapshot -> {
            throw new IllegalStateException("unexpected backend failure");
        };
        service.requestSave(null);
        executor.runNext();
        storage.beforeSave = snapshot -> { };

        assertTrue(service.flush(0));
        assertEquals(2, storage.attempts.get());
    }

    @Test
    void concurrentRegionRequestsPersistEveryPointWithOnlyOneWriter() {
        RecordingStorage storage = new RecordingStorage();
        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            AsyncSaveService service = service(storage, executor);
            CompletableFuture<?>[] regions = new CompletableFuture<?>[8];
            for (int region = 0; region < regions.length; region++) {
                int regionId = region;
                regions[region] = CompletableFuture.runAsync(() -> {
                    for (int i = 0; i < 100; i++) {
                        storage.setWarp("r" + regionId + "point" + i, point(i));
                        service.requestSave(null);
                    }
                });
            }
            CompletableFuture.allOf(regions).join();

            assertTrue(service.shutdown(5_000));
            assertEquals(800, storage.saved.getLast().warps().size());
            assertEquals(1, storage.maximumWriters.get());
        }
    }

    @Test
    void failedIdleShutdownCleanupClosesOnceAndDoesNotRetryDirtyData() {
        RecordingStorage storage = new RecordingStorage();
        QueuedExecutor executor = new QueuedExecutor();
        AsyncSaveService service = service(storage, executor);
        service.requestSave(null);
        storage.failNext.set(true);
        assertFalse(service.shutdown(0));

        service.closeStorageWhenIdle();
        service.closeStorageWhenIdle();
        executor.runNext();
        assertFalse(service.flush(0));
        assertFalse(service.shutdown(0));
        assertEquals(1, storage.closes.get());
        assertEquals(1, storage.attempts.get());
    }

    @Test
    void timedOutShutdownCleanupClosesOnlyAfterActiveWriterCompletes() throws Exception {
        RecordingStorage storage = new RecordingStorage();
        QueuedExecutor executor = new QueuedExecutor();
        AsyncSaveService service = service(storage, executor);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        storage.beforeSave = blockOnce(entered, release);
        storage.setSpawn(point(1));
        service.requestSave(null);
        CompletableFuture<Void> worker = CompletableFuture.runAsync(executor::runNext);
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            storage.setSpawn(point(2));
            assertFalse(service.shutdown(0));
            service.closeStorageWhenIdle();
            service.closeStorageWhenIdle();
            assertEquals(0, storage.closes.get());
            assertFalse(service.flush(0));
        } finally {
            release.countDown();
        }
        worker.get(5, TimeUnit.SECONDS);

        service.closeStorageWhenIdle();
        assertEquals(1, storage.closes.get());
        assertEquals(2, storage.attempts.get());
        assertEquals(List.of(point(1), point(2)), storage.saved.stream().map(PointStorageSnapshot::spawn).toList());
        assertEquals(0, storage.activeWritersAtClose.get());
    }

    @Test
    void failedRetiredWriterClosesWithoutRetryingPendingShutdownRevision() throws Exception {
        RecordingStorage storage = new RecordingStorage();
        QueuedExecutor executor = new QueuedExecutor();
        AsyncSaveService service = service(storage, executor);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        storage.beforeSave = blockOnce(entered, release);
        storage.failNext.set(true);
        service.requestSave(null);
        CompletableFuture<Void> worker = CompletableFuture.runAsync(executor::runNext);
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertFalse(service.shutdown(0));
            service.closeStorageWhenIdle();
        } finally {
            release.countDown();
        }
        worker.get(5, TimeUnit.SECONDS);

        assertEquals(1, storage.attempts.get());
        assertTrue(storage.saved.isEmpty());
        assertEquals(1, storage.closes.get());
        assertEquals(0, storage.activeWritersAtClose.get());
    }

    @Test
    void cleanupRequiresRetirementAndHandlesCloseFailureIdempotently() {
        RecordingStorage storage = new RecordingStorage();
        AsyncSaveService service = service(storage, new QueuedExecutor());
        assertThrows(IllegalStateException.class, service::closeStorageWhenIdle);
        assertTrue(service.retireIfClean());
        storage.failClose.set(true);

        service.closeStorageWhenIdle();
        service.closeStorageWhenIdle();
        assertEquals(1, storage.closes.get());
    }

    private static AsyncSaveService service(RecordingStorage storage, Executor executor) {
        return new AsyncSaveService(storage, logger(), executor, player -> { });
    }

    private static Logger logger() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        return logger;
    }

    private static Consumer<PointStorageSnapshot> blockOnce(CountDownLatch entered, CountDownLatch release) {
        AtomicBoolean first = new AtomicBoolean(true);
        return snapshot -> {
            if (first.compareAndSet(true, false)) {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Test did not release writer");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }
        };
    }

    private static StoredPoint point(double x) {
        return StoredPoint.of("world", x, 64, 0, 0, 0, "", "", "", true, "test", 1);
    }

    private static final class QueuedExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        void runNext() {
            tasks.remove().run();
        }
    }

    private static final class RecordingStorage extends AbstractPointStorage {
        private final List<PointStorageSnapshot> saved = new CopyOnWriteArrayList<>();
        private final AtomicInteger snapshots = new AtomicInteger();
        private final AtomicInteger attempts = new AtomicInteger();
        private final AtomicInteger activeWriters = new AtomicInteger();
        private final AtomicInteger maximumWriters = new AtomicInteger();
        private final AtomicBoolean failNext = new AtomicBoolean();
        private final AtomicInteger closes = new AtomicInteger();
        private final AtomicInteger activeWritersAtClose = new AtomicInteger();
        private final AtomicBoolean failClose = new AtomicBoolean();
        private volatile Consumer<PointStorageSnapshot> beforeSave = snapshot -> { };

        @Override
        public void load() {
        }

        @Override
        public PointStorageSnapshot snapshot() {
            snapshots.incrementAndGet();
            return super.snapshot();
        }

        @Override
        public void save(PointStorageSnapshot snapshot) {
            attempts.incrementAndGet();
            int active = activeWriters.incrementAndGet();
            maximumWriters.accumulateAndGet(active, Math::max);
            try {
                beforeSave.accept(snapshot);
                if (failNext.compareAndSet(true, false)) {
                    throw new StorageException("test failure");
                }
                saved.add(snapshot);
            } finally {
                activeWriters.decrementAndGet();
            }
        }

        @Override
        public void close() {
            closes.incrementAndGet();
            activeWritersAtClose.set(activeWriters.get());
            if (failClose.get()) {
                throw new StorageException("test close failure");
            }
        }
    }
}
