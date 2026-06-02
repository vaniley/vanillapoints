package dev.vaniley.vanillapoints.api;

import org.bukkit.Location;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API for VanillaPoints. Mutation methods and Location-returning methods must be called on the server main
 * thread because they interact with Bukkit worlds and events. PointInfo read methods return immutable DTOs.
 */
public interface VanillaPointsAPI {
    Optional<Location> getSpawn();

    Optional<Location> getHome(UUID playerId);

    Optional<Location> getWarp(String name);

    Optional<PointInfo> getSpawnInfo();

    Optional<PointInfo> getHomeInfo(UUID playerId);

    Optional<PointInfo> getWarpInfo(String name);

    boolean setSpawn(Location location);

    boolean setHome(UUID playerId, Location location);

    boolean setWarp(String name, Location location);

    boolean setWarp(String name, Location location, PointMetadata metadata);

    boolean deleteWarp(String name);

    Collection<String> listWarps();

    boolean isValidWarpName(String name);

    void reload();
}
