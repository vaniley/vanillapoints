package dev.vaniley.vanillapoints.api;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Optional;

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

    public Optional<Location> toLocation() {
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
