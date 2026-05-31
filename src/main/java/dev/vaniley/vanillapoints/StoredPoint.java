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

    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    private StoredPoint(String worldName, double x, double y, double z, float yaw, float pitch) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
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
                point.getPitch()
        );
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
                hasNumber(section, PITCH) ? (float) section.getDouble(PITCH) : 0.0F
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
}
