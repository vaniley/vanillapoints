package dev.vaniley.vanillapoints;

import java.util.Map;
import java.util.UUID;

/**
 * Immutable copy of all stored points. {@code spawns} is keyed by world name; the empty string key holds the
 * global (non per-world) spawn.
 */
record PointStorageSnapshot(Map<String, StoredPoint> spawns, Map<UUID, Map<String, StoredPoint>> homes, Map<String, StoredPoint> warps) {
    static final String GLOBAL_SPAWN_KEY = "";

    PointStorageSnapshot {
        spawns = Map.copyOf(spawns);
        homes = homes.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> Map.copyOf(entry.getValue())
        ));
        warps = Map.copyOf(warps);
    }

    static PointStorageSnapshot empty() {
        return new PointStorageSnapshot(Map.of(), Map.of(), Map.of());
    }

    StoredPoint spawn() {
        return spawns.get(GLOBAL_SPAWN_KEY);
    }

    boolean isEmpty() {
        return spawns.isEmpty() && homes.isEmpty() && warps.isEmpty();
    }
}
