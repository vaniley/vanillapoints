package dev.vaniley.vanillapoints.api;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Optional;

/** Immutable coordinate/metadata snapshot, safe to read on any thread. */
public record PointInfo(
        String worldName,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        String description,
        String icon,
        String category,
        boolean publicVisible,
        String createdBy,
        long createdAt
) {
    public PointInfo {
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalArgumentException("worldName must not be blank");
        }
        description = normalize(description);
        icon = normalize(icon);
        category = normalize(category);
        createdBy = normalize(createdBy);
        createdAt = Math.max(0L, createdAt);
    }

    /**
     * Backwards-compatible constructor without category/visibility. Points default to public with no category.
     */
    public PointInfo(
            String worldName,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            String description,
            String icon,
            String createdBy,
            long createdAt
    ) {
        this(worldName, x, y, z, yaw, pitch, description, icon, "", true, createdBy, createdAt);
    }

    public int blockX() {
        return (int) Math.floor(x);
    }

    public int blockY() {
        return (int) Math.floor(y);
    }

    public int blockZ() {
        return (int) Math.floor(z);
    }

    /**
     * Resolves the currently loaded world without loading a chunk or teleporting anything.
     * Call only on a Paper main thread or a Folia region/global tick thread. The returned
     * location grants no permission to access its region's chunks or entities.
     *
     * @throws IllegalStateException if called from an asynchronous worker
     */
    public Optional<Location> toLocation() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Location conversion requires a server tick thread (Paper main, Folia region or global)");
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return Optional.empty();
        }
        return Optional.of(new Location(world, x, y, z, yaw, pitch));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
