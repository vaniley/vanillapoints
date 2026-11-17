package dev.vaniley.vanillapoints;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

final class AsyncSaveService {
    private final PointStorage storage;
    private final Logger logger;
    private final Executor executor;
    private final Consumer<Player> failureNotifier;
    private final Object monitor = new Object();

    // All state belongs to monitor. Requests are cheap; only the writer copies the storage.
    private long requestedRevision;
    private long savedRevision;
    private long workerGeneration;
    private boolean workerScheduled;
    private boolean writerRunning;
    private boolean retired;
    private boolean closeRequested;
    private boolean closeStarted;
    private Player latestNotifyPlayer;

    AsyncSaveService(JavaPlugin plugin, PointStorage storage, MessageService messages) {
        this(storage, plugin.getLogger(),
                task -> Bukkit.getAsyncScheduler().runNow(plugin, ignored -> task.run()),
                player -> {
                    if (plugin.isEnabled()) {
                        player.getScheduler().run(plugin, ignored -> {
                            if (player.isOnline()) {
                                player.sendMessage(messages.component(player, "data-save-error"));
                            }
                        }, null);
                    }
                });
    }

    AsyncSaveService(PointStorage storage, Logger logger, Executor executor, Consumer<Player> failureNotifier) {
        this.storage = storage;
        this.logger = logger;
        this.executor = executor;
        this.failureNotifier = failureNotifier;
    }

    void requestSave(Player notifyPlayer) {
        long generation;
        synchronized (monitor) {
            if (retired) {
                return;
            }
            requestedRevision++;
            latestNotifyPlayer = notifyPlayer;
            if (workerScheduled || writerRunning) {
                return;
            }
            workerScheduled = true;
            generation = ++workerGeneration;
        }

        try {
            executor.execute(() -> runWorker(generation));
        } catch (RuntimeException exception) {
            synchronized (monitor) {
                if (workerScheduled && workerGeneration == generation) {
                    workerScheduled = false;
                    monitor.notifyAll();
                }
            }
            logger.log(Level.SEVERE, "Could not schedule VanillaPoints data save; data remains pending.", exception);
        }
    }

    boolean saveNow() {
        synchronized (monitor) {
            if (retired) {
                return false;
            }
            requestedRevision++;
        }
        return flush(Long.MAX_VALUE);
    }

    /**
     * Stops accepting requests and saves even mutations made with immediate saving disabled.
     * Queued scheduler callbacks become no-ops. Callers must quiesce mutations before shutdown,
     * and must not close the storage directly unless this returns true. To abandon unsaved data
     * after failure, use closeStorageWhenIdle() instead.
     */
    boolean shutdown(long timeoutMillis) {
        synchronized (monitor) {
            if (!retired) {
                retired = true;
                requestedRevision++;
            }
            invalidateQueuedWorker();
        }
        return flush(timeoutMillis);
    }

    /** Retires an already-flushed service without another write. Callers must quiesce mutations first. */
    boolean retireIfClean() {
        synchronized (monitor) {
            if (writerRunning || savedRevision != requestedRevision) {
                return false;
            }
            retired = true;
            latestNotifyPlayer = null;
            invalidateQueuedWorker();
            return true;
        }
    }

    /** Closes retired storage after any active writer drains pending revisions or fails. */
    void closeStorageWhenIdle() {
        synchronized (monitor) {
            if (!retired) {
                throw new IllegalStateException("Storage must be retired before requesting cleanup");
            }
            closeRequested = true;
            latestNotifyPlayer = null;
            invalidateQueuedWorker();
            monitor.notifyAll();
        }
        closeStorageIfRequested();
    }

    private void closeStorageIfRequested() {
        synchronized (monitor) {
            if (!closeRequested || writerRunning || closeStarted) {
                return;
            }
            closeStarted = true;
        }
        try {
            storage.close();
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Could not close VanillaPoints storage after shutdown.", exception);
        }
    }

    /**
     * Waits for an active writer, or takes over a queued save synchronously. The timeout bounds
     * waiting for another writer; storage I/O itself is bounded by the backend's I/O timeouts.
     * Failed data stays dirty for the next request, explicit flush, or shutdown retry.
     */
    boolean flush(long timeoutMillis) {
        long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0L, timeoutMillis));
        long started = System.nanoTime();
        synchronized (monitor) {
            while (writerRunning && !closeRequested) {
                long remaining = timeoutNanos - (System.nanoTime() - started);
                if (remaining <= 0L) {
                    logger.severe("Timed out while flushing VanillaPoints data.");
                    return false;
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(monitor, remaining);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            if (closeRequested) {
                return false;
            }
            invalidateQueuedWorker();
            if (savedRevision == requestedRevision) {
                return true;
            }
            writerRunning = true;
        }
        return writePending();
    }

    private void invalidateQueuedWorker() {
        workerScheduled = false;
        workerGeneration++;
    }

    private void runWorker(long generation) {
        synchronized (monitor) {
            if (!workerScheduled || generation != workerGeneration || retired) {
                return;
            }
            workerScheduled = false;
            writerRunning = true;
        }
        writePending();
    }

    private boolean writePending() {
        boolean released = false;
        try {
            while (true) {
                long revision;
                Player notifyPlayer;
                synchronized (monitor) {
                    if (savedRevision == requestedRevision) {
                        latestNotifyPlayer = null;
                        // Release atomically with the clean check, so a new request cannot be stranded.
                        writerRunning = false;
                        released = true;
                        monitor.notifyAll();
                        return true;
                    }
                    revision = requestedRevision;
                    notifyPlayer = latestNotifyPlayer;
                }

                try {
                    storage.save(storage.snapshot());
                } catch (RuntimeException exception) {
                    logger.log(Level.SEVERE, "Could not save VanillaPoints data; data remains pending.", exception);
                    notifySaveFailure(notifyPlayer);
                    synchronized (monitor) {
                        // Honor requests that arrived during the failed write, but never spin-retry
                        // the same revision when a backend remains unavailable.
                        if (!retired && requestedRevision != revision) {
                            continue;
                        }
                        writerRunning = false;
                        released = true;
                        monitor.notifyAll();
                        return false;
                    }
                }

                synchronized (monitor) {
                    savedRevision = revision;
                }
            }
        } finally {
            if (!released) {
                synchronized (monitor) {
                    writerRunning = false;
                    monitor.notifyAll();
                }
            }
            closeStorageIfRequested();
        }
    }

    private void notifySaveFailure(Player player) {
        synchronized (monitor) {
            if (player == null || retired) {
                return;
            }
        }
        try {
            failureNotifier.accept(player);
        } catch (RuntimeException exception) {
            // A disconnect or plugin disable must not break dirty-state retention or writer release.
            logger.log(Level.FINE, "Could not notify player about a VanillaPoints save failure.", exception);
        }
    }
}
