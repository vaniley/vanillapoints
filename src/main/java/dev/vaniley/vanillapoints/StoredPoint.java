package dev.vaniley.vanillapoints;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Objects;

final class StoredPoint {
    private static final String WORLD = "world";
    private static final String X = "x";
    private static final String Y = "y";
    private static final String Z = "z";
    private static final String YAW = "yaw";
    private static final String PITCH = "pitch";
    private static final String DESCRIPTION = "description";
    private static final String ICON = "icon";
    private static final String CATEGORY = "category";
    private static final String PUBLIC = "public";
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
    private final String category;
    private final boolean publicVisible;
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
            String category,
            boolean publicVisible,
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
        this.category = normalizeOptional(category);
        this.publicVisible = publicVisible;
        this.createdBy = normalizeOptional(createdBy);
        this.createdAt = Math.max(0L, createdAt);
    }

    static StoredPoint of(
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
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalArgumentException("worldName must not be blank");
        }

        return new StoredPoint(worldName, x, y, z, yaw, pitch, description, icon, category, publicVisible, createdBy, createdAt);
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
                true,
                "",
                0L
        );
    }

    StoredPoint withMetadata(String description, String icon, String category, boolean publicVisible, String createdBy, long createdAt) {
        return new StoredPoint(worldName, x, y, z, yaw, pitch, description, icon, category, publicVisible, createdBy, createdAt);
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
                section.getString(CATEGORY, ""),
                section.getBoolean(PUBLIC, true),
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
        setOptional(section, CATEGORY, category);
        if (!publicVisible) {
            section.set(PUBLIC, false);
        }
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

    double x() {
        return x;
    }

    double y() {
        return y;
    }

    double z() {
        return z;
    }

    float yaw() {
        return yaw;
    }

    float pitch() {
        return pitch;
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

    String category() {
        return category;
    }

    boolean publicVisible() {
        return publicVisible;
    }

    String createdBy() {
        return createdBy;
    }

    long createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StoredPoint point)) {
            return false;
        }
        return Double.compare(x, point.x) == 0
                && Double.compare(y, point.y) == 0
                && Double.compare(z, point.z) == 0
                && Float.compare(yaw, point.yaw) == 0
                && Float.compare(pitch, point.pitch) == 0
                && publicVisible == point.publicVisible
                && createdAt == point.createdAt
                && worldName.equals(point.worldName)
                && description.equals(point.description)
                && icon.equals(point.icon)
                && category.equals(point.category)
                && createdBy.equals(point.createdBy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldName, x, y, z, yaw, pitch, description, icon, category, publicVisible, createdBy, createdAt);
    }
}
