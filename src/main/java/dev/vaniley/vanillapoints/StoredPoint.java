package dev.vaniley.vanillapoints;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

final class StoredPoint {
    private static final String WORLD = "world";
    private static final String X = "x";
    private static final String Y = "y";
    private static final String Z = "z";
    private static final String YAW = "yaw";
    private static final String PITCH = "pitch";
    private static final String DESCRIPTION = "description";
    private static final String ICON = "icon";
    private static final String CREATED_BY = "created-by";
    private static final String CREATED_AT = "created-at";

    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final String description;
    private final String icon;
    private final String createdBy;
    private final long createdAt;

    private StoredPoint(
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
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.description = normalizeOptional(description);
        this.icon = normalizeOptional(icon);
        this.createdBy = normalizeOptional(createdBy);
        this.createdAt = Math.max(0L, createdAt);
    }

    static StoredPoint fromLocation(Location location, boolean normalizeToBlock) {
        Location point = normalizeToBlock ? location.getBlock().getLocation() : location;
        World world = point.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Cannot store a location without a world");
        }

        return new StoredPoint(
                world.getName(),
                point.getX(),
                point.getY(),
                point.getZ(),
                point.getYaw(),
                point.getPitch(),
                "",
                "",
                "",
                0L
        );
    }

    StoredPoint withMetadata(String description, String icon, String createdBy, long createdAt) {
        return new StoredPoint(worldName, x, y, z, yaw, pitch, description, icon, createdBy, createdAt);
    }

    static StoredPoint fromSection(ConfigurationSection section) {
        String worldName = section.getString(WORLD);
        if (worldName == null || worldName.isBlank()) {
            return null;
        }
        if (!hasNumber(section, X) || !hasNumber(section, Y) || !hasNumber(section, Z)) {
            return null;
        }

        return new StoredPoint(
                worldName,
                section.getDouble(X),
                section.getDouble(Y),
                section.getDouble(Z),
                hasNumber(section, YAW) ? (float) section.getDouble(YAW) : 0.0F,
                hasNumber(section, PITCH) ? (float) section.getDouble(PITCH) : 0.0F,
                section.getString(DESCRIPTION, ""),
                section.getString(ICON, ""),
                section.getString(CREATED_BY, ""),
                hasNumber(section, CREATED_AT) ? section.getLong(CREATED_AT) : 0L
        );
    }

    private static boolean hasNumber(ConfigurationSection section, String key) {
        return section.isDouble(key) || section.isInt(key) || section.isLong(key);
    }

    void save(ConfigurationSection section) {
        section.set(WORLD, worldName);
        section.set(X, x);
        section.set(Y, y);
        section.set(Z, z);
        section.set(YAW, yaw);
        section.set(PITCH, pitch);
        setOptional(section, DESCRIPTION, description);
        setOptional(section, ICON, icon);
        setOptional(section, CREATED_BY, createdBy);
        if (createdAt > 0L) {
            section.set(CREATED_AT, createdAt);
        }
    }

    private static void setOptional(ConfigurationSection section, String key, String value) {
        if (!value.isBlank()) {
            section.set(key, value);
        }
    }

    private static String normalizeOptional(String value) {
        return value == null ? "" : value.trim();
    }

    String worldName() {
        return worldName;
    }

    int blockX() {
        return (int) Math.floor(x);
    }

    int blockY() {
        return (int) Math.floor(y);
    }

    int blockZ() {
        return (int) Math.floor(z);
    }

    String description() {
        return description;
    }

    String icon() {
        return icon;
    }

    String createdBy() {
        return createdBy;
    }

    long createdAt() {
        return createdAt;
    }
}
