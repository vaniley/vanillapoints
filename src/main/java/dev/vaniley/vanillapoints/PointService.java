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

final class PointService {
    private final JavaPlugin plugin;
    private final PointStorage storage;
    private final AsyncSaveService saveService;
    private final boolean normalizeToBlock;
    private final boolean saveImmediately;

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
        if (!ensureMainThread("setSpawn")) {
            return PointMutationResult.INVALID;
        }

        StoredPoint point = StoredPoint.fromLocation(location, normalizeToBlock);
        SpawnSetEvent event = new SpawnSetEvent(actorId(actor), actorName(actor), storage.spawn().map(PointInfoMapper::toInfo).orElse(null), PointInfoMapper.toInfo(point));
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return PointMutationResult.CANCELLED;
        }

        storage.setSpawn(point);
        requestSave(actor);
        return PointMutationResult.SUCCESS;
    }

    PointMutationResult setHome(CommandSender actor, UUID playerId, Location location) {
        return setHome(actor, playerId, PointStorage.DEFAULT_HOME_NAME, location, PointMetadata.empty());
    }

    PointMutationResult setHome(CommandSender actor, UUID playerId, String name, Location location, PointMetadata metadata) {
        if (!ensureMainThread("setHome")) {
            return PointMutationResult.INVALID;
        }
        if (!PointStorage.isValidHomeName(name)) {
            return PointMutationResult.INVALID;
        }

        String normalizedName = PointStorage.normalizeHomeName(name);
        StoredPoint point = PointInfoMapper.applyMetadata(StoredPoint.fromLocation(location, normalizeToBlock), metadata);
        HomeSetEvent event = new HomeSetEvent(actorId(actor), actorName(actor), playerId, storage.home(playerId, normalizedName).map(PointInfoMapper::toInfo).orElse(null), PointInfoMapper.toInfo(point));
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return PointMutationResult.CANCELLED;
        }

        storage.setHome(playerId, normalizedName, point);
        requestSave(actor);
        return PointMutationResult.SUCCESS;
    }

    PointMutationResult deleteHome(CommandSender actor, UUID playerId, String name) {
        if (!ensureMainThread("deleteHome")) {
            return PointMutationResult.INVALID;
        }
        if (!PointStorage.isValidHomeName(name)) {
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
        requestSave(actor);
        return PointMutationResult.SUCCESS;
    }

    PointMutationResult setWarp(CommandSender actor, String name, Location location, PointMetadata metadata) {
        if (!ensureMainThread("setWarp")) {
            return PointMutationResult.INVALID;
        }
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
        requestSave(actor);
        return PointMutationResult.SUCCESS;
    }

    PointMutationResult deleteWarp(CommandSender actor, String name) {
        if (!ensureMainThread("deleteWarp")) {
            return PointMutationResult.INVALID;
        }
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
        requestSave(actor);
        return PointMutationResult.SUCCESS;
    }

    private void requestSave(CommandSender actor) {
        if (!saveImmediately) {
            return;
        }
        saveService.requestSave(actor instanceof Player player ? player : null);
    }

    private boolean ensureMainThread(String operation) {
        if (Bukkit.isPrimaryThread()) {
            return true;
        }

        plugin.getLogger().warning("VanillaPoints API mutation '" + operation + "' was called off the main thread and was rejected.");
        return false;
    }

    private UUID actorId(CommandSender actor) {
        return actor instanceof Player player ? player.getUniqueId() : null;
    }

    private String actorName(CommandSender actor) {
        return actor == null ? null : actor.getName();
    }
}
