package dev.vaniley.vanillapoints;

import java.util.HashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

abstract class AbstractPointStorage implements PointStorage {
    private final Object lock = new Object();
    private StoredPoint spawn;
    private Map<UUID, StoredPoint> homes = new HashMap<>();
    private Map<String, StoredPoint> warps = new HashMap<>();

    @Override
    public Optional<StoredPoint> spawn() {
        synchronized (lock) {
            return Optional.ofNullable(spawn);
        }
    }

    @Override
    public void setSpawn(StoredPoint point) {
        synchronized (lock) {
            spawn = point;
        }
    }

    @Override
    public Optional<StoredPoint> home(UUID playerId) {
        synchronized (lock) {
            return Optional.ofNullable(homes.get(playerId));
        }
    }

    @Override
    public void setHome(UUID playerId, StoredPoint point) {
        synchronized (lock) {
            homes.put(playerId, point);
        }
    }

    @Override
    public Optional<StoredPoint> warp(String name) {
        synchronized (lock) {
            return Optional.ofNullable(warps.get(PointStorage.normalizeWarpName(name)));
        }
    }

    @Override
    public void setWarp(String name, StoredPoint point) {
        synchronized (lock) {
            warps.put(PointStorage.normalizeWarpName(name), point);
        }
    }

    @Override
    public boolean deleteWarp(String name) {
        synchronized (lock) {
            return warps.remove(PointStorage.normalizeWarpName(name)) != null;
        }
    }

    @Override
    public Set<String> warpNames() {
        synchronized (lock) {
            return Collections.unmodifiableSet(new TreeSet<>(warps.keySet()));
        }
    }

    @Override
    public PointStorageSnapshot snapshot() {
        synchronized (lock) {
            return new PointStorageSnapshot(spawn, new HashMap<>(homes), new HashMap<>(warps));
        }
    }

    @Override
    public void replace(PointStorageSnapshot snapshot) {
        synchronized (lock) {
            spawn = snapshot.spawn();
            homes = new HashMap<>(snapshot.homes());
            warps = new HashMap<>(snapshot.warps());
        }
    }
}
