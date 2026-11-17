package dev.vaniley.vanillapoints.api;

import org.bukkit.Location;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Coordinate-only API for VanillaPoints; no method loads chunks or teleports entities.
 *
 * <p>Mutations must run on the Paper main thread or a Folia region/global tick thread.
 * Calls from asynchronous workers are rejected with {@code false}; use the appropriate
 * region/entity/global scheduler first. Mutation events are synchronous/cancellable and
 * run on the calling thread. A Folia tick thread does not imply ownership of another region's
 * entities or chunks. Listeners must not wait for another region or perform nested point
 * mutations (nested writes return {@code false}); read methods remain available.</p>
 *
 * <p>PointInfo reads and name lists are immutable snapshots safe to obtain/read on any
 * thread. Location-returning methods (including {@link PointInfo#toLocation()}) require
 * a server tick thread and throw {@link IllegalStateException} on async workers; resolving
 * a Location does not authorize access to the world data at that location. Callers must
 * not modify an input Location concurrently with a mutation.</p>
 *
 * <p>Retained API references follow the active service across reloads. When the plugin
 * is disabled, reads return empty snapshots and mutations return {@code false}.
 * {@link #reload()} queues lifecycle work on the global scheduler and may be called
 * from any thread; it does not complete the reload before returning.</p>
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
