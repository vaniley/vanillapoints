package dev.vaniley.vanillapoints;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

final class AsyncSaveService {
    private final JavaPlugin plugin;
    private final PointStorage storage;
    private final MessageService messages;
    private final AtomicBoolean workerRunning = new AtomicBoolean(false);
    private final Object monitor = new Object();

    private volatile PointStorageSnapshot latestSnapshot;
    private volatile UUID latestNotifyPlayerId;

    AsyncSaveService(JavaPlugin plugin, PointStorage storage, MessageService messages) {
        this.plugin = plugin;
        this.storage = storage;
        this.messages = messages;
    }

    void requestSave(Player notifyPlayer) {
        latestSnapshot = storage.snapshot();
        latestNotifyPlayerId = notifyPlayer == null ? null : notifyPlayer.getUniqueId();
        startWorkerIfNeeded();
    }

    boolean saveNow() {
        PointStorageSnapshot snapshot = storage.snapshot();
        try {
            storage.save(snapshot);
            latestSnapshot = null;
            return true;
        } catch (StorageException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save VanillaPoints data.", exception);
            return false;
        }
    }

    boolean flush(long timeoutMillis) {
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMillis);

        while (System.currentTimeMillis() <= deadline) {
            if (!workerRunning.get()) {
                PointStorageSnapshot pending = latestSnapshot;
                if (pending == null) {
                    return true;
                }
                try {
                    storage.save(pending);
                    if (latestSnapshot == pending) {
                        latestSnapshot = null;
                    }
                    return true;
                } catch (StorageException exception) {
                    plugin.getLogger().log(Level.SEVERE, "Could not flush VanillaPoints data.", exception);
                    return false;
                }
            }

            synchronized (monitor) {
                try {
                    monitor.wait(Math.min(100L, Math.max(1L, deadline - System.currentTimeMillis())));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        plugin.getLogger().severe("Timed out while flushing VanillaPoints data.");
        return false;
    }

    private void startWorkerIfNeeded() {
        if (!workerRunning.compareAndSet(false, true)) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::runWorker);
    }

    private void runWorker() {
        try {
            while (true) {
                PointStorageSnapshot snapshot = latestSnapshot;
                UUID notifyPlayerId = latestNotifyPlayerId;
                if (snapshot == null) {
                    return;
                }

                try {
                    storage.save(snapshot);
                    if (latestSnapshot == snapshot) {
                        latestSnapshot = null;
                        latestNotifyPlayerId = null;
                    }
                } catch (StorageException exception) {
                    plugin.getLogger().log(Level.SEVERE, "Could not save VanillaPoints data.", exception);
                    notifySaveFailure(notifyPlayerId);
                    if (latestSnapshot == snapshot) {
                        latestSnapshot = null;
                        latestNotifyPlayerId = null;
                    }
                }
            }
        } finally {
            workerRunning.set(false);
            synchronized (monitor) {
                monitor.notifyAll();
            }
            if (latestSnapshot != null) {
                startWorkerIfNeeded();
            }
        }
    }

    private void notifySaveFailure(UUID playerId) {
        if (playerId == null || !plugin.isEnabled()) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage(messages.component(player, "data-save-error"));
            }
        });
    }
}
