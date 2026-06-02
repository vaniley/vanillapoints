package dev.vaniley.vanillapoints;

import java.util.Map;
import java.util.UUID;

record PointStorageSnapshot(StoredPoint spawn, Map<UUID, StoredPoint> homes, Map<String, StoredPoint> warps) {
    PointStorageSnapshot {
        homes = Map.copyOf(homes);
        warps = Map.copyOf(warps);
    }

    static PointStorageSnapshot empty() {
        return new PointStorageSnapshot(null, Map.of(), Map.of());
    }

    boolean isEmpty() {
        return spawn == null && homes.isEmpty() && warps.isEmpty();
    }
}
