package dev.vaniley.vanillapoints;

import dev.vaniley.vanillapoints.api.PointMetadata;
import dev.vaniley.vanillapoints.api.event.HomeSetEvent;
import dev.vaniley.vanillapoints.api.event.SpawnSetEvent;
import dev.vaniley.vanillapoints.api.event.WarpSetEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PointServiceThreadTest {
    private final JavaPlugin plugin = mock(JavaPlugin.class);
    private final PluginManager events = mock(PluginManager.class);
    private final AsyncSaveService saves = mock(AsyncSaveService.class);
    private final MemoryStorage storage = new MemoryStorage();
    // Bukkit Locations retain worlds weakly; keep the mocked world alive for each test.
    private final World world = mock(World.class);

    private PointService service(boolean normalize) {
        when(plugin.getLogger()).thenReturn(Logger.getLogger("PointServiceThreadTest"));
        return new PointService(plugin, storage, saves, normalize, true);
    }

    private Location location() {
        when(world.getName()).thenReturn("world");
        return new Location(world, 3.9, 64.7, -1.1, 90.0F, 45.0F);
    }

    private <T> T serverContext(boolean tickThread, Supplier<T> action) {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(tickThread);
            bukkit.when(Bukkit::getPluginManager).thenReturn(events);
            return action.get();
        }
    }

    @Test
    void acceptsServerTickContextWithoutTouchingBlocksAndUsesSynchronousEvents() {
        PointService points = service(true);
        Location location = spy(location());
        doAnswer(invocation -> {
            SpawnSetEvent event = invocation.getArgument(0);
            assertFalse(event.isAsynchronous());
            assertTrue(storage.spawn().isEmpty(), "event must precede the write");
            return null;
        }).when(events).callEvent(any(Event.class));

        // Paper's main thread and Folia's region/global tick threads all report true.
        assertEquals(PointMutationResult.SUCCESS, serverContext(true, () -> points.setSpawn(null, location)));
        StoredPoint point = storage.spawn().orElseThrow();
        assertEquals(3.0, point.x());
        assertEquals(64.0, point.y());
        assertEquals(-2.0, point.z());
        assertEquals(0.0F, point.yaw());
        assertEquals(0.0F, point.pitch());
        verify(location, never()).getBlock();
        verify(saves).requestSave(null);
    }

    @Test
    void rejectsEveryMutationFromAnAsyncWorkerBeforeEventsOrStorageChanges() {
        PointService points = service(false);
        UUID owner = UUID.randomUUID();
        Location location = location();
        serverContext(false, () -> {
            assertEquals(PointMutationResult.INVALID, points.setSpawn(null, location));
            assertEquals(PointMutationResult.INVALID, points.setHome(null, owner, location));
            assertEquals(PointMutationResult.INVALID, points.setWarp(null, "warp", location, PointMetadata.empty()));
            assertEquals(PointMutationResult.INVALID, points.deleteSpawn(null, null));
            assertEquals(PointMutationResult.INVALID, points.deleteHome(null, owner, "home"));
            assertEquals(PointMutationResult.INVALID, points.deleteWarp(null, "warp"));
            return null;
        });
        assertTrue(storage.snapshot().spawns().isEmpty());
        assertTrue(storage.snapshot().homes().isEmpty());
        assertTrue(storage.snapshot().warps().isEmpty());
        verifyNoInteractions(events, saves);
    }

    @Test
    void cancellationPreventsSetAndDeleteWritesAndSaveRequests() {
        PointService points = service(false);
        UUID owner = UUID.randomUUID();
        Location location = location();
        StoredPoint old = StoredPoint.of("world", 1, 2, 3, 0, 0, "", "", "", true, "", 0);
        storage.setHome(owner, "home", old);
        storage.setWarp("warp", old);
        doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            assertFalse(event.isAsynchronous());
            ((Cancellable) event).setCancelled(true);
            return null;
        }).when(events).callEvent(any(Event.class));

        serverContext(true, () -> {
            assertEquals(PointMutationResult.CANCELLED, points.setSpawn(null, location));
            assertEquals(PointMutationResult.CANCELLED, points.setHome(null, owner, "home", location, PointMetadata.empty()));
            assertEquals(PointMutationResult.CANCELLED, points.setWarp(null, "warp", location, PointMetadata.empty()));
            assertEquals(PointMutationResult.CANCELLED, points.deleteHome(null, owner, "home"));
            assertEquals(PointMutationResult.CANCELLED, points.deleteWarp(null, "warp"));
            return null;
        });
        assertTrue(storage.spawn().isEmpty());
        assertSame(old, storage.home(owner, "home").orElseThrow());
        assertSame(old, storage.warp("warp").orElseThrow());
        verifyNoInteractions(saves);
    }

    @Test
    void listenerMayReadButNestedMutationCannotClobberOuterChange() {
        PointService points = service(false);
        Location location = location();
        doAnswer(invocation -> {
            assertTrue(points.warp("nested").isEmpty());
            assertEquals(PointMutationResult.INVALID, points.setWarp(null, "nested", location, PointMetadata.empty()));
            return null;
        }).when(events).callEvent(any(Event.class));

        assertEquals(PointMutationResult.SUCCESS, serverContext(true,
                () -> points.setWarp(null, "outer", location, PointMetadata.empty())));
        assertTrue(storage.warp("outer").isPresent());
        assertTrue(storage.warp("nested").isEmpty());
        verify(events, times(1)).callEvent(any(Event.class));
    }

    @Test
    void invalidLocationsNeverEmitEventsOrPersist() {
        PointService points = service(false);
        Location nonFinite = location();
        nonFinite.setX(Double.NaN);
        Location unloaded = spy(location());
        doThrow(new IllegalArgumentException("World unloaded")).when(unloaded).getWorld();
        serverContext(true, () -> {
            assertEquals(PointMutationResult.INVALID, points.setSpawn(null, (Location) null));
            assertEquals(PointMutationResult.INVALID, points.setSpawn(null, new Location(null, 0, 0, 0)));
            assertEquals(PointMutationResult.INVALID, points.setSpawn(null, nonFinite));
            assertEquals(PointMutationResult.INVALID, points.setSpawn(null, unloaded));
            return null;
        });
        verifyNoInteractions(events, saves);
    }

    @Test
    void concurrentTickContextsObserveCommittedPreviousEventValue() throws Exception {
        PointService points = service(false);
        Location first = location();
        Location second = location();
        second.setX(100);
        CountDownLatch firstEvent = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondAttempt = new CountDownLatch(1);
        CountDownLatch secondEvent = new CountDownLatch(1);
        doAnswer(invocation -> {
            WarpSetEvent event = invocation.getArgument(0);
            if (event.newPoint().x() == first.getX()) {
                assertNull(event.oldPoint());
                firstEvent.countDown();
                assertTrue(releaseFirst.await(5, TimeUnit.SECONDS));
            } else {
                secondEvent.countDown();
                assertNotNull(event.oldPoint());
                assertEquals(first.getX(), event.oldPoint().x());
            }
            return null;
        }).when(events).callEvent(any(Event.class));

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<PointMutationResult> a = executor.submit(() -> serverContext(true,
                    () -> points.setWarp(null, "shared", first, PointMetadata.empty())));
            assertTrue(firstEvent.await(5, TimeUnit.SECONDS));
            Future<PointMutationResult> b = executor.submit(() -> serverContext(true, () -> {
                secondAttempt.countDown();
                return points.setWarp(null, "shared", second, PointMetadata.empty());
            }));
            try {
                assertTrue(secondAttempt.await(5, TimeUnit.SECONDS));
                assertFalse(secondEvent.await(100, TimeUnit.MILLISECONDS), "second event must wait for the first commit");
            } finally {
                releaseFirst.countDown();
            }
            assertEquals(PointMutationResult.SUCCESS, a.get(5, TimeUnit.SECONDS));
            assertEquals(PointMutationResult.SUCCESS, b.get(5, TimeUnit.SECONDS));
        }
        assertEquals(second.getX(), storage.warp("shared").orElseThrow().x());
    }

    @Test
    void homeLimitCheckAndWriteAreAtomicAcrossTickContexts() throws Exception {
        PointService points = service(false);
        UUID owner = UUID.randomUUID();
        Location location = location();
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<PointMutationResult>> tasks = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            String name = "home" + i;
            tasks.add(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return serverContext(true, () -> points.setHome(null, owner, name, location, PointMetadata.empty(), 1));
            });
        }
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<PointMutationResult>> results = new ArrayList<>();
            tasks.forEach(task -> results.add(executor.submit(task)));
            start.countDown();
            int successful = 0;
            for (Future<PointMutationResult> result : results) {
                PointMutationResult value = result.get(5, TimeUnit.SECONDS);
                assertTrue(value == PointMutationResult.SUCCESS || value == PointMutationResult.LIMIT_REACHED);
                if (value == PointMutationResult.SUCCESS) {
                    successful++;
                }
            }
            assertEquals(1, successful);
        }
        assertEquals(1, storage.homes(owner).size());
        verify(events, times(1)).callEvent(any(HomeSetEvent.class));
        verify(saves, times(1)).requestSave(null);
        String existing = storage.homes(owner).keySet().iterator().next();
        assertEquals(PointMutationResult.SUCCESS, serverContext(true,
                () -> points.setHome(null, owner, existing, location, PointMetadata.empty(), 1)));
    }

    private static final class MemoryStorage extends AbstractPointStorage {
        @Override
        public void load() {
        }

        @Override
        public void save(PointStorageSnapshot snapshot) {
        }
    }
}
