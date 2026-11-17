package dev.vaniley.vanillapoints;

import java.util.HashMap;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

abstract class AbstractPointStorage implements PointStorage {
    private final Object lock = new Object();
    private Map<String, StoredPoint> spawns = new HashMap<>();
    private Map<UUID, Map<String, StoredPoint>> homes = new HashMap<>();
    private Map<String, StoredPoint> warps = new HashMap<>();
    private PointStorageSnapshot cachedSnapshot = PointStorageSnapshot.empty();

    private static String spawnKey(String worldName) {
        return worldName == null ? PointStorageSnapshot.GLOBAL_SPAWN_KEY : worldName.toLowerCase(Locale.ROOT);
    }

    @Override
    public Optional<StoredPoint> spawn() {
        synchronized (lock) {
            return Optional.ofNullable(spawns.get(PointStorageSnapshot.GLOBAL_SPAWN_KEY));
        }
    }

    @Override
    public void setSpawn(StoredPoint point) {
        synchronized (lock) {
            spawns.put(PointStorageSnapshot.GLOBAL_SPAWN_KEY, point);
            cachedSnapshot = null;
        }
    }

    @Override
    public Optional<StoredPoint> spawn(String worldName) {
        synchronized (lock) {
            return Optional.ofNullable(spawns.get(spawnKey(worldName)));
        }
    }

    @Override
    public void setSpawn(String worldName, StoredPoint point) {
        synchronized (lock) {
            spawns.put(spawnKey(worldName), point);
            cachedSnapshot = null;
        }
    }

    @Override
    public boolean deleteSpawn(String worldName) {
        synchronized (lock) {
            boolean removed = spawns.remove(spawnKey(worldName)) != null;
            if (removed) {
                cachedSnapshot = null;
            }
            return removed;
        }
    }

    @Override
    public Map<String, StoredPoint> spawns() {
        synchronized (lock) {
            return Map.copyOf(spawns);
        }
    }

    @Override
    public Optional<StoredPoint> home(UUID playerId) {
        return home(playerId, PointStorage.DEFAULT_HOME_NAME);
    }

    @Override
    public Optional<StoredPoint> home(UUID playerId, String name) {
        synchronized (lock) {
            return Optional.ofNullable(homes.getOrDefault(playerId, Map.of()).get(PointStorage.normalizeHomeName(name)));
        }
    }

    @Override
    public Map<String, StoredPoint> homes(UUID playerId) {
        synchronized (lock) {
            return Map.copyOf(homes.getOrDefault(playerId, Map.of()));
        }
    }

    @Override
    public void setHome(UUID playerId, StoredPoint point) {
        setHome(playerId, PointStorage.DEFAULT_HOME_NAME, point);
    }

    @Override
    public void setHome(UUID playerId, String name, StoredPoint point) {
        synchronized (lock) {
            homes.computeIfAbsent(playerId, ignored -> new HashMap<>()).put(PointStorage.normalizeHomeName(name), point);
            cachedSnapshot = null;
        }
    }

    @Override
    public boolean deleteHome(UUID playerId, String name) {
        synchronized (lock) {
            Map<String, StoredPoint> playerHomes = homes.get(playerId);
            if (playerHomes == null || playerHomes.remove(PointStorage.normalizeHomeName(name)) == null) {
                return false;
            }
            if (playerHomes.isEmpty()) {
                homes.remove(playerId);
            }
            cachedSnapshot = null;
            return true;
        }
    }

    @Override
    public Set<String> homeNames(UUID playerId) {
        synchronized (lock) {
            return Collections.unmodifiableSet(new TreeSet<>(homes.getOrDefault(playerId, Map.of()).keySet()));
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
            cachedSnapshot = null;
        }
    }

    @Override
    public boolean deleteWarp(String name) {
        synchronized (lock) {
            boolean removed = warps.remove(PointStorage.normalizeWarpName(name)) != null;
            if (removed) {
                cachedSnapshot = null;
            }
            return removed;
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
            if (cachedSnapshot == null) {
                // The snapshot constructor already takes a deep immutable copy under this lock.
                cachedSnapshot = new PointStorageSnapshot(spawns, homes, warps);
            }
            return cachedSnapshot;
        }
    }

    @Override
    public void replace(PointStorageSnapshot snapshot) {
        synchronized (lock) {
            spawns = new HashMap<>(snapshot.spawns());
            homes = new HashMap<>();
            snapshot.homes().forEach((playerId, playerHomes) -> homes.put(playerId, new HashMap<>(playerHomes)));
            warps = new HashMap<>(snapshot.warps());
            cachedSnapshot = snapshot;
        }
    }
}
