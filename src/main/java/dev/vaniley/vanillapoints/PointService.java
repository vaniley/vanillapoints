package dev.vaniley.vanillapoints;

import dev.vaniley.vanillapoints.api.PointMetadata;
import dev.vaniley.vanillapoints.api.event.HomeSetEvent;
import dev.vaniley.vanillapoints.api.event.HomeDeletedEvent;
import dev.vaniley.vanillapoints.api.event.SpawnSetEvent;
import dev.vaniley.vanillapoints.api.event.WarpDeletedEvent;
import dev.vaniley.vanillapoints.api.event.WarpSetEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

final class PointService {
    private final JavaPlugin plugin;
    private final PointStorage storage;
    private final AsyncSaveService saveService;
    private final boolean normalizeToBlock;
    private final boolean saveImmediately;
    private final Object mutationLock = new Object();
    private boolean mutationInProgress;

    PointService(JavaPlugin plugin, PointStorage storage, AsyncSaveService saveService, boolean normalizeToBlock, boolean saveImmediately) {
        this.plugin = plugin;
        this.storage = storage;
        this.saveService = saveService;
        this.normalizeToBlock = normalizeToBlock;
        this.saveImmediately = saveImmediately;
    }

    Optional<StoredPoint> spawn() {
        return storage.spawn();
    }

    Optional<StoredPoint> spawn(String worldName) {
        return storage.spawn(worldName);
    }

    Optional<StoredPoint> home(UUID playerId) {
        return storage.home(playerId);
    }

    Optional<StoredPoint> home(UUID playerId, String name) {
        return storage.home(playerId, name);
    }

    Map<String, StoredPoint> homes(UUID playerId) {
        return storage.homes(playerId);
    }

    Optional<StoredPoint> warp(String name) {
        return storage.warp(name);
    }

    Collection<String> warpNames() {
        return storage.warpNames();
    }

    PointMutationResult setSpawn(CommandSender actor, Location location) {
        return setSpawn(actor, null, location);
    }

    PointMutationResult setSpawn(CommandSender actor, String worldName, Location location) {
        if (!ensureServerThread("setSpawn")) {
            return PointMutationResult.INVALID;
        }
        if (!validLocation(location)) {
            return PointMutationResult.INVALID;
        }

        return mutate(actor, () -> {
            StoredPoint point = StoredPoint.fromLocation(location, normalizeToBlock);
            StoredPoint old = storage.spawn(worldName).orElse(null);
            SpawnSetEvent event = new SpawnSetEvent(actorId(actor), actorName(actor), old == null ? null : PointInfoMapper.toInfo(old), PointInfoMapper.toInfo(point));
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return PointMutationResult.CANCELLED;
            }

            storage.setSpawn(worldName, point);
            return PointMutationResult.SUCCESS;
        });
    }

    PointMutationResult deleteSpawn(CommandSender actor, String worldName) {
        if (!ensureServerThread("deleteSpawn")) {
            return PointMutationResult.INVALID;
        }

        return mutate(actor, () -> {
            if (storage.spawn(worldName).isEmpty()) {
                return PointMutationResult.NOT_FOUND;
            }

            storage.deleteSpawn(worldName);
            return PointMutationResult.SUCCESS;
        });
    }

    PointMutationResult setHome(CommandSender actor, UUID playerId, Location location) {
        return setHome(actor, playerId, PointStorage.DEFAULT_HOME_NAME, location, PointMetadata.empty());
    }

    PointMutationResult setHome(CommandSender actor, UUID playerId, String name, Location location, PointMetadata metadata) {
        return setHome(actor, playerId, name, location, metadata, Integer.MAX_VALUE);
    }

    PointMutationResult setHome(CommandSender actor, UUID playerId, String name, Location location, PointMetadata metadata, int maxHomes) {
        if (!ensureServerThread("setHome")) {
            return PointMutationResult.INVALID;
        }
        if (!validLocation(location)) {
            return PointMutationResult.INVALID;
        }

        return mutate(actor, () -> {
            if (playerId == null || !PointStorage.isValidHomeName(name)) {
                return PointMutationResult.INVALID;
            }

            String normalizedName = PointStorage.normalizeHomeName(name);
            if (maxHomes != Integer.MAX_VALUE
                    && storage.home(playerId, normalizedName).isEmpty()
                    && storage.homes(playerId).size() >= maxHomes) {
                return PointMutationResult.LIMIT_REACHED;
            }
            StoredPoint point = PointInfoMapper.applyMetadata(StoredPoint.fromLocation(location, normalizeToBlock), metadata);
            HomeSetEvent event = new HomeSetEvent(actorId(actor), actorName(actor), playerId, storage.home(playerId, normalizedName).map(PointInfoMapper::toInfo).orElse(null), PointInfoMapper.toInfo(point));
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return PointMutationResult.CANCELLED;
            }

            storage.setHome(playerId, normalizedName, point);
            return PointMutationResult.SUCCESS;
        });
    }

    PointMutationResult deleteHome(CommandSender actor, UUID playerId, String name) {
        if (!ensureServerThread("deleteHome")) {
            return PointMutationResult.INVALID;
        }

        return mutate(actor, () -> {
            if (playerId == null || !PointStorage.isValidHomeName(name)) {
                return PointMutationResult.INVALID;
            }

            String normalizedName = PointStorage.normalizeHomeName(name);
            Optional<StoredPoint> existing = storage.home(playerId, normalizedName);
            if (existing.isEmpty()) {
                return PointMutationResult.NOT_FOUND;
            }

            HomeDeletedEvent event = new HomeDeletedEvent(actorId(actor), actorName(actor), playerId, PointInfoMapper.toInfo(existing.get()));
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return PointMutationResult.CANCELLED;
            }

            storage.deleteHome(playerId, normalizedName);
            return PointMutationResult.SUCCESS;
        });
    }

    PointMutationResult setWarp(CommandSender actor, String name, Location location, PointMetadata metadata) {
        if (!ensureServerThread("setWarp")) {
            return PointMutationResult.INVALID;
        }
        if (!validLocation(location)) {
            return PointMutationResult.INVALID;
        }

        return mutate(actor, () -> {
            if (!PointStorage.isValidWarpName(name)) {
                return PointMutationResult.INVALID;
            }

            String normalizedName = PointStorage.normalizeWarpName(name);
            StoredPoint point = PointInfoMapper.applyMetadata(StoredPoint.fromLocation(location, normalizeToBlock), metadata);
            WarpSetEvent event = new WarpSetEvent(actorId(actor), actorName(actor), normalizedName, storage.warp(normalizedName).map(PointInfoMapper::toInfo).orElse(null), PointInfoMapper.toInfo(point));
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return PointMutationResult.CANCELLED;
            }

            storage.setWarp(normalizedName, point);
            return PointMutationResult.SUCCESS;
        });
    }

    PointMutationResult deleteWarp(CommandSender actor, String name) {
        if (!ensureServerThread("deleteWarp")) {
            return PointMutationResult.INVALID;
        }

        return mutate(actor, () -> {
            if (!PointStorage.isValidWarpName(name)) {
                return PointMutationResult.INVALID;
            }

            String normalizedName = PointStorage.normalizeWarpName(name);
            Optional<StoredPoint> existing = storage.warp(normalizedName);
            if (existing.isEmpty()) {
                return PointMutationResult.NOT_FOUND;
            }

            WarpDeletedEvent event = new WarpDeletedEvent(actorId(actor), actorName(actor), normalizedName, PointInfoMapper.toInfo(existing.get()));
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return PointMutationResult.CANCELLED;
            }

            storage.deleteWarp(normalizedName);
            return PointMutationResult.SUCCESS;
        });
    }

    /** Serializes only plugin-owned data; never schedules or waits for another region. */
    private PointMutationResult mutate(CommandSender actor, Supplier<PointMutationResult> operation) {
        synchronized (mutationLock) {
            // A listener can read points, but a nested write would invalidate the outer event's old value.
            if (mutationInProgress) {
                return PointMutationResult.INVALID;
            }
            mutationInProgress = true;
            try {
                PointMutationResult result = operation.get();
                if (result == PointMutationResult.SUCCESS) {
                    requestSave(actor);
                }
                return result;
            } finally {
                mutationInProgress = false;
            }
        }
    }

    private boolean validLocation(Location location) {
        try {
            // Location keeps a weak world reference and throws if that world was unloaded.
            if (location == null || location.getWorld() == null) {
                return false;
            }
            location.checkFinite();
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void requestSave(CommandSender actor) {
        if (!saveImmediately) {
            return;
        }
        saveService.requestSave(actor instanceof Player player ? player : null);
    }

    private boolean ensureServerThread(String operation) {
        // Paper/Folia delegate this to TickThread.isTickThread(): region AND global tick threads
        // are accepted. It does not imply ownership of any entity/chunk; we only store coordinates.
        if (Bukkit.isPrimaryThread()) {
            return true;
        }

        plugin.getLogger().warning("VanillaPoints API mutation '" + operation + "' requires a server tick thread (Paper main, Folia region or global) and was rejected.");
        return false;
    }

    private UUID actorId(CommandSender actor) {
        return actor instanceof Player player ? player.getUniqueId() : null;
    }

    private String actorName(CommandSender actor) {
        return actor == null ? null : actor.getName();
    }
}
