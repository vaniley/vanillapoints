package dev.vaniley.vanillapoints;

import java.util.Map;
import java.util.UUID;

record PointStorageSnapshot(StoredPoint spawn, Map<UUID, Map<String, StoredPoint>> homes, Map<String, StoredPoint> warps) {
    PointStorageSnapshot {
        homes = homes.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> Map.copyOf(entry.getValue())
        ));
        warps = Map.copyOf(warps);
    }

    static PointStorageSnapshot empty() {
        return new PointStorageSnapshot(null, Map.of(), Map.of());
    }

    boolean isEmpty() {
        return spawn == null && homes.isEmpty() && warps.isEmpty();
    }
}
