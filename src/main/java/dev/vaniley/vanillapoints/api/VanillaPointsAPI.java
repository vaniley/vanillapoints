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

    Optional<Location> getSpawn(String worldName);

    Optional<PointInfo> getSpawnInfo(String worldName);

    boolean setSpawn(String worldName, Location location);

    boolean deleteSpawn(String worldName);

    Optional<Location> getHome(UUID playerId);

    Optional<Location> getHome(UUID playerId, String name);

    Optional<Location> getWarp(String name);

    Optional<PointInfo> getSpawnInfo();

    Optional<PointInfo> getHomeInfo(UUID playerId);

    Optional<PointInfo> getHomeInfo(UUID playerId, String name);

    Optional<PointInfo> getWarpInfo(String name);

    boolean setSpawn(Location location);

    boolean setHome(UUID playerId, Location location);

    boolean setHome(UUID playerId, String name, Location location);

    boolean deleteHome(UUID playerId, String name);

    Collection<String> listHomes(UUID playerId);

    boolean isValidHomeName(String name);

    boolean setWarp(String name, Location location);

    boolean setWarp(String name, Location location, PointMetadata metadata);

    boolean deleteWarp(String name);

    Collection<String> listWarps();

    boolean isValidWarpName(String name);

    void reload();
}
