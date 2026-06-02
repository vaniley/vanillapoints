package dev.vaniley.vanillapoints;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

interface PointStorage extends AutoCloseable {
    Pattern WARP_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    void load() throws StorageException;

    Optional<StoredPoint> spawn();

    void setSpawn(StoredPoint point);

    Optional<StoredPoint> home(UUID playerId);

    void setHome(UUID playerId, StoredPoint point);

    Optional<StoredPoint> warp(String name);

    void setWarp(String name, StoredPoint point);

    boolean deleteWarp(String name);

    Set<String> warpNames();

    PointStorageSnapshot snapshot();

    void replace(PointStorageSnapshot snapshot);

    void save(PointStorageSnapshot snapshot) throws StorageException;

    @Override
    default void close() throws StorageException {
    }

    static String normalizeWarpName(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase(Locale.ROOT);
    }

    static boolean isValidWarpName(String name) {
        return name != null && WARP_NAME_PATTERN.matcher(name).matches();
    }
}
