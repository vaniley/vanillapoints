package dev.vaniley.vanillapoints;

import dev.vaniley.vanillapoints.api.PointInfo;
import dev.vaniley.vanillapoints.api.PointMetadata;
import dev.vaniley.vanillapoints.api.VanillaPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

final class VanillaPointsApiProvider implements VanillaPointsAPI {
    private final VanillaPoints plugin;

    VanillaPointsApiProvider(VanillaPoints plugin, PointService points) {
        this.plugin = plugin;
    }

    @Override
    public Optional<Location> getSpawn() {
        requireServerThread();
        return getSpawnInfo().flatMap(PointInfo::toLocation);
    }

    @Override
    public Optional<Location> getSpawn(String worldName) {
        requireServerThread();
        return getSpawnInfo(worldName).flatMap(PointInfo::toLocation);
    }

    @Override
    public Optional<PointInfo> getSpawnInfo(String worldName) {
        return withPoints(points -> points.spawn(worldName).map(PointInfoMapper::toInfo), Optional.empty());
    }

    @Override
    public boolean setSpawn(String worldName, Location location) {
        return location != null && withPoints(points -> points.setSpawn(null, worldName, location) == PointMutationResult.SUCCESS, false);
    }

    @Override
    public boolean deleteSpawn(String worldName) {
        return withPoints(points -> points.deleteSpawn(null, worldName) == PointMutationResult.SUCCESS, false);
    }

    @Override
    public Optional<Location> getHome(UUID playerId) {
        requireServerThread();
        return getHomeInfo(playerId).flatMap(PointInfo::toLocation);
    }

    @Override
    public Optional<Location> getHome(UUID playerId, String name) {
        requireServerThread();
        return getHomeInfo(playerId, name).flatMap(PointInfo::toLocation);
    }

    @Override
    public Optional<Location> getWarp(String name) {
        requireServerThread();
        return getWarpInfo(name).flatMap(PointInfo::toLocation);
    }

    @Override
    public Optional<PointInfo> getSpawnInfo() {
        return withPoints(points -> points.spawn().map(PointInfoMapper::toInfo), Optional.empty());
    }

    @Override
    public Optional<PointInfo> getHomeInfo(UUID playerId) {
        return playerId == null ? Optional.empty() : withPoints(points -> points.home(playerId).map(PointInfoMapper::toInfo), Optional.empty());
    }

    @Override
    public Optional<PointInfo> getHomeInfo(UUID playerId, String name) {
        if (playerId == null || !PointStorage.isValidHomeName(name)) {
            return Optional.empty();
        }
        return withPoints(points -> points.home(playerId, name).map(PointInfoMapper::toInfo), Optional.empty());
    }

    @Override
    public Optional<PointInfo> getWarpInfo(String name) {
        if (!PointStorage.isValidWarpName(name)) {
            return Optional.empty();
        }
        return withPoints(points -> points.warp(name).map(PointInfoMapper::toInfo), Optional.empty());
    }

    @Override
    public boolean setSpawn(Location location) {
        return location != null && withPoints(points -> points.setSpawn(null, location) == PointMutationResult.SUCCESS, false);
    }

    @Override
    public boolean setHome(UUID playerId, Location location) {
        return playerId != null && location != null && withPoints(points -> points.setHome(null, playerId, location) == PointMutationResult.SUCCESS, false);
    }

    @Override
    public boolean setHome(UUID playerId, String name, Location location) {
        return playerId != null
                && PointStorage.isValidHomeName(name)
                && location != null
                && withPoints(points -> points.setHome(null, playerId, name, location, PointMetadata.empty()) == PointMutationResult.SUCCESS, false);
    }

    @Override
    public boolean deleteHome(UUID playerId, String name) {
        return playerId != null && PointStorage.isValidHomeName(name) && withPoints(points -> points.deleteHome(null, playerId, name) == PointMutationResult.SUCCESS, false);
    }

    @Override
    public Collection<String> listHomes(UUID playerId) {
        return playerId == null ? List.of() : withPoints(points -> List.copyOf(points.homes(playerId).keySet()), List.of());
    }

    @Override
    public boolean setWarp(String name, Location location) {
        return setWarp(name, location, PointMetadata.empty());
    }

    @Override
    public boolean setWarp(String name, Location location, PointMetadata metadata) {
        return PointStorage.isValidWarpName(name)
                && location != null
                && withPoints(points -> points.setWarp(null, name, location, metadata) == PointMutationResult.SUCCESS, false);
    }

    @Override
    public boolean deleteWarp(String name) {
        return PointStorage.isValidWarpName(name) && withPoints(points -> points.deleteWarp(null, name) == PointMutationResult.SUCCESS, false);
    }

    @Override
    public Collection<String> listWarps() {
        return withPoints(points -> List.copyOf(points.warpNames()), List.of());
    }

    @Override
    public boolean isValidWarpName(String name) {
        return PointStorage.isValidWarpName(name);
    }

    @Override
    public boolean isValidHomeName(String name) {
        return PointStorage.isValidHomeName(name);
    }

    @Override
    public void reload() {
        plugin.reloadFromApi();
    }

    private <T> T withPoints(Function<PointService, T> operation, T unavailable) {
        return plugin.withState(() -> {
            if (!plugin.isEnabled()) {
                return unavailable;
            }
            PointService points = plugin.currentPoints();
            return points == null ? unavailable : operation.apply(points);
        });
    }

    private void requireServerThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Location reads require a server tick thread (Paper main, Folia region or global)");
        }
    }
}
