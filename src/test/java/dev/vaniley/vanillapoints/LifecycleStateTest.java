package dev.vaniley.vanillapoints;

import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LifecycleStateTest {
    private VanillaPoints plugin() throws Exception {
        VanillaPoints plugin = mock(VanillaPoints.class, CALLS_REAL_METHODS);
        set(plugin, "stateLock", new ReentrantReadWriteLock());
        set(plugin, "reloadQueued", new AtomicBoolean());
        set(plugin, "importQueued", new AtomicBoolean());
        set(plugin, "pendingWarpDeletions", new ConcurrentHashMap<>());
        doReturn(Logger.getAnonymousLogger()).when(plugin).getLogger();
        return plugin;
    }

    @Test
    void maintenanceReturnsFallbackWithoutCallingAction() throws Exception {
        VanillaPoints plugin = plugin();
        field(plugin, "reloadQueued", AtomicBoolean.class).set(true);
        AtomicBoolean called = new AtomicBoolean();
        assertEquals("busy", plugin.tryWithState(() -> {
            called.set(true);
            return "value";
        }, "busy"));
        assertFalse(called.get());
    }

    @Test
    void readsNeverWaitForReloadWriter() throws Exception {
        VanillaPoints plugin = plugin();
        ReentrantReadWriteLock lock = field(plugin, "stateLock", ReentrantReadWriteLock.class);
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var writer = executor.submit(() -> {
                lock.writeLock().lock();
                try {
                    acquired.countDown();
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                } finally {
                    lock.writeLock().unlock();
                }
            });
            assertTrue(acquired.await(5, TimeUnit.SECONDS));
            try {
                assertTimeoutPreemptively(Duration.ofMillis(500), () ->
                        assertEquals("busy", plugin.tryWithState(() -> "value", "busy")));
            } finally {
                release.countDown();
            }
            writer.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void releasesReadLockAfterActionFailure() throws Exception {
        VanillaPoints plugin = plugin();
        assertThrows(IllegalArgumentException.class, () -> plugin.tryWithState(() -> {
            throw new IllegalArgumentException("test");
        }, false));
        assertEquals(0, field(plugin, "stateLock", ReentrantReadWriteLock.class).getReadLockCount());
        assertTrue(plugin.tryWithState(() -> true, false));
    }

    @Test
    void existingOperationsCanFinishNestedReadsAfterReloadIsQueued() throws Exception {
        VanillaPoints plugin = plugin();
        ReentrantReadWriteLock lock = field(plugin, "stateLock", ReentrantReadWriteLock.class);
        lock.readLock().lock();
        try {
            field(plugin, "reloadQueued", AtomicBoolean.class).set(true);
            assertEquals("value", plugin.tryWithState(() -> "value", "busy"));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Test
    void failedFlushClearsReloadFlagWithoutRetiringWorkingServices() throws Exception {
        VanillaPoints plugin = plugin();
        AsyncSaveService save = mock(AsyncSaveService.class);
        set(plugin, "saveService", save);
        doReturn(true).when(plugin).isEnabled();
        doReturn(new YamlConfiguration()).when(plugin).getConfig();
        field(plugin, "reloadQueued", AtomicBoolean.class).set(true);
        prepareReload(plugin);
        assertFalse(field(plugin, "reloadQueued", AtomicBoolean.class).get());
        verify(save).flush(10_000L);
        verify(save, never()).retireIfClean();
        verify(plugin, never()).getServer();
    }

    @Test
    void failedPreparationRestoresConfigurationAndLeavesOldServiceActive() throws Exception {
        VanillaPoints plugin = plugin();
        AsyncSaveService save = mock(AsyncSaveService.class);
        set(plugin, "saveService", save);
        when(save.flush(10_000L)).thenReturn(true);
        when(save.saveNow()).thenReturn(true);
        doReturn(true).when(plugin).isEnabled();
        var original = new YamlConfiguration();
        original.set("storage.backend", "yaml");
        var failed = new YamlConfiguration();
        failed.set("storage.backend", "mysql");
        failed.set("new-key", "discard");
        AtomicReference<YamlConfiguration> config = new AtomicReference<>(original);
        doAnswer(call -> config.get()).when(plugin).getConfig();
        doAnswer(call -> {
            config.set(failed);
            throw new IllegalArgumentException("invalid candidate config");
        }).when(plugin).reloadConfig();
        field(plugin, "reloadQueued", AtomicBoolean.class).set(true);
        prepareReload(plugin);
        assertFalse(field(plugin, "reloadQueued", AtomicBoolean.class).get());
        assertEquals("yaml", failed.getString("storage.backend"));
        assertFalse(failed.contains("new-key"));
        verify(save, never()).retireIfClean();
    }

    @Test
    void failedShutdownDefersCloseAndInvalidatesApiService() throws Exception {
        VanillaPoints plugin = plugin();
        Server server = mock(Server.class);
        when(server.getServicesManager()).thenReturn(mock(ServicesManager.class));
        doReturn(server).when(plugin).getServer();
        AsyncSaveService save = mock(AsyncSaveService.class);
        PointStorage storage = mock(PointStorage.class);
        set(plugin, "saveService", save);
        set(plugin, "storage", storage);
        set(plugin, "points", mock(PointService.class));
        field(plugin, "reloadQueued", AtomicBoolean.class).set(true);
        field(plugin, "importQueued", AtomicBoolean.class).set(true);
        plugin.onDisable();
        assertFalse(field(plugin, "reloadQueued", AtomicBoolean.class).get());
        assertFalse(field(plugin, "importQueued", AtomicBoolean.class).get());
        verify(save).shutdown(10_000L);
        verify(save).closeStorageWhenIdle();
        verify(storage, never()).close();
        assertNull(plugin.currentPoints());
        assertNull(field(plugin, "storage", PointStorage.class));
    }

    @Test
    void importMergeUpdatesLiveStorageAndRequestsSave() throws Exception {
        VanillaPoints plugin = plugin();
        PointStorage storage = mock(PointStorage.class);
        AsyncSaveService save = mock(AsyncSaveService.class);
        MessageService messages = mock(MessageService.class);
        var sender = mock(org.bukkit.command.CommandSender.class);
        doReturn(true).when(plugin).isEnabled();
        set(plugin, "storage", storage);
        set(plugin, "saveService", save);
        set(plugin, "messages", messages);
        when(messages.component(eq(sender), eq("admin-import-done"), anyMap()))
                .thenReturn(net.kyori.adventure.text.Component.text("imported"));
        var playerId = java.util.UUID.randomUUID();
        StoredPoint point = StoredPoint.of("world", 1, 64, 2, 0, 0, "", "", "", true, "Essentials", 0);
        var snapshot = new PointStorageSnapshot(java.util.Map.of(),
                java.util.Map.of(playerId, java.util.Map.of("home", point)), java.util.Map.of("market", point));
        var imported = new EssentialsImporter.ImportedData(snapshot, new EssentialsImporter.Result(1, 1));
        field(plugin, "importQueued", AtomicBoolean.class).set(true);
        mergeImport(plugin, sender, imported, 5);
        verify(storage).setHome(playerId, "home", point);
        verify(storage).setWarp("market", point);
        verify(save).requestSave(null);
        assertFalse(field(plugin, "importQueued", AtomicBoolean.class).get());
    }

    @Test
    void contendedImportRetriesWithoutReparsingOrBlockingRegion() throws Exception {
        VanillaPoints plugin = plugin();
        Server server = mock(Server.class);
        var scheduler = mock(io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler.class);
        when(server.getGlobalRegionScheduler()).thenReturn(scheduler);
        doReturn(server).when(plugin).getServer();
        doReturn(true).when(plugin).isEnabled();
        var sender = mock(org.bukkit.command.CommandSender.class);
        var imported = new EssentialsImporter.ImportedData(PointStorageSnapshot.empty(), new EssentialsImporter.Result(0, 0));
        field(plugin, "importQueued", AtomicBoolean.class).set(true);
        ReentrantReadWriteLock lock = field(plugin, "stateLock", ReentrantReadWriteLock.class);
        lock.readLock().lock();
        try {
            mergeImport(plugin, sender, imported, 5);
            verify(scheduler).runDelayed(eq(plugin), any(), eq(1L));
            assertTrue(field(plugin, "importQueued", AtomicBoolean.class).get());
        } finally {
            lock.readLock().unlock();
        }
    }

    private void mergeImport(VanillaPoints plugin, org.bukkit.command.CommandSender sender,
                             EssentialsImporter.ImportedData data, int retries) throws Exception {
        var method = VanillaPoints.class.getDeclaredMethod("mergeImport", org.bukkit.command.CommandSender.class,
                EssentialsImporter.ImportedData.class, int.class);
        method.setAccessible(true);
        method.invoke(plugin, sender, data, retries);
    }

    private void prepareReload(VanillaPoints plugin) throws Exception {
        var method = VanillaPoints.class.getDeclaredMethod("prepareReload", org.bukkit.command.CommandSender.class);
        method.setAccessible(true);
        method.invoke(plugin, new Object[]{null});
    }

    private void set(Object target, String name, Object value) throws Exception {
        Field field = VanillaPoints.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private <T> T field(Object target, String name, Class<T> type) throws Exception {
        Field field = VanillaPoints.class.getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(target));
    }
}
