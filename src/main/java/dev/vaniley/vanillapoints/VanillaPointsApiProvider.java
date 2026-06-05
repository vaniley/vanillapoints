package dev.vaniley.vanillapoints;

import dev.vaniley.vanillapoints.api.PointInfo;
import dev.vaniley.vanillapoints.api.PointMetadata;
import dev.vaniley.vanillapoints.api.VanillaPointsAPI;
import org.bukkit.Location;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

final class VanillaPointsApiProvider implements VanillaPointsAPI {
    private final VanillaPoints plugin;
    private final PointService points;

    VanillaPointsApiProvider(VanillaPoints plugin, PointService points) {
        this.plugin = plugin;
        this.points = points;
    }

    @Override
    public Optional<Location> getSpawn() {
        return getSpawnInfo().flatMap(PointInfo::toLocation);
    }

    @Override
    public Optional<Location> getHome(UUID playerId) {
        return getHomeInfo(playerId).flatMap(PointInfo::toLocation);
    }

    @Override
    public Optional<Location> getHome(UUID playerId, String name) {
        return getHomeInfo(playerId, name).flatMap(PointInfo::toLocation);
    }

    @Override
    public Optional<Location> getWarp(String name) {
        return getWarpInfo(name).flatMap(PointInfo::toLocation);
    }

    @Override
    public Optional<PointInfo> getSpawnInfo() {
        return points.spawn().map(PointInfoMapper::toInfo);
    }

    @Override
    public Optional<PointInfo> getHomeInfo(UUID playerId) {
        return playerId == null ? Optional.empty() : points.home(playerId).map(PointInfoMapper::toInfo);
    }

    @Override
    public Optional<PointInfo> getHomeInfo(UUID playerId, String name) {
        if (playerId == null || !PointStorage.isValidHomeName(name)) {
            return Optional.empty();
        }
        return points.home(playerId, name).map(PointInfoMapper::toInfo);
    }

    @Override
    public Optional<PointInfo> getWarpInfo(String name) {
        if (!PointStorage.isValidWarpName(name)) {
            return Optional.empty();
        }
        return points.warp(name).map(PointInfoMapper::toInfo);
    }

    @Override
    public boolean setSpawn(Location location) {
        return location != null && points.setSpawn(null, location) == PointMutationResult.SUCCESS;
    }

    @Override
    public boolean setHome(UUID playerId, Location location) {
        return playerId != null && location != null && points.setHome(null, playerId, location) == PointMutationResult.SUCCESS;
    }

    @Override
    public boolean setHome(UUID playerId, String name, Location location) {
        return playerId != null
                && PointStorage.isValidHomeName(name)
                && location != null
                && points.setHome(null, playerId, name, location, PointMetadata.empty()) == PointMutationResult.SUCCESS;
    }

    @Override
    public boolean deleteHome(UUID playerId, String name) {
        return playerId != null && PointStorage.isValidHomeName(name) && points.deleteHome(null, playerId, name) == PointMutationResult.SUCCESS;
    }

    @Override
    public Collection<String> listHomes(UUID playerId) {
        return playerId == null ? java.util.List.of() : points.homes(playerId).keySet();
    }

    @Override
    public boolean setWarp(String name, Location location) {
        return setWarp(name, location, PointMetadata.empty());
    }

    @Override
    public boolean setWarp(String name, Location location, PointMetadata metadata) {
        return PointStorage.isValidWarpName(name)
                && location != null
                && points.setWarp(null, name, location, metadata) == PointMutationResult.SUCCESS;
    }

    @Override
    public boolean deleteWarp(String name) {
        return PointStorage.isValidWarpName(name) && points.deleteWarp(null, name) == PointMutationResult.SUCCESS;
    }

    @Override
    public Collection<String> listWarps() {
        return points.warpNames();
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
}
