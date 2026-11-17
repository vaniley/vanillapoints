package dev.vaniley.vanillapoints;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractPointStorageTest {
    @Test
    void snapshotsAreCachedUntilAnySuccessfulMutation() {
        MemoryStorage storage = new MemoryStorage();
        UUID owner = UUID.randomUUID();
        PointStorageSnapshot before = storage.snapshot();
        assertSame(before, storage.snapshot());

        storage.setSpawn(point(1));
        before = changedSnapshot(storage, before);
        storage.setSpawn("world", point(2));
        before = changedSnapshot(storage, before);
        assertTrue(storage.deleteSpawn("world"));
        before = changedSnapshot(storage, before);
        storage.setHome(owner, point(3));
        before = changedSnapshot(storage, before);
        storage.setHome(owner, "mine", point(4));
        before = changedSnapshot(storage, before);
        assertTrue(storage.deleteHome(owner, "mine"));
        before = changedSnapshot(storage, before);
        assertTrue(storage.deleteHome(owner, PointStorage.DEFAULT_HOME_NAME));
        before = changedSnapshot(storage, before);
        storage.setWarp("shop", point(5));
        before = changedSnapshot(storage, before);
        assertTrue(storage.deleteWarp("shop"));
        before = changedSnapshot(storage, before);
        assertTrue(storage.deleteSpawn(null));
        before = changedSnapshot(storage, before);
        assertTrue(before.isEmpty());

        assertFalse(storage.deleteWarp("missing"));
        assertFalse(storage.deleteHome(owner, "missing"));
        assertFalse(storage.deleteSpawn("missing"));
        assertSame(before, storage.snapshot());
    }

    @Test
    void oldSnapshotsAndReadViewsNeverChangeAfterMutations() {
        MemoryStorage storage = new MemoryStorage();
        UUID owner = UUID.randomUUID();
        storage.setHome(owner, "base", point(1));
        storage.setSpawn(point(2));
        storage.setWarp("shop", point(3));
        PointStorageSnapshot snapshot = storage.snapshot();
        Map<String, StoredPoint> homes = storage.homes(owner);
        var homeNames = storage.homeNames(owner);
        var warpNames = storage.warpNames();
        var spawns = storage.spawns();

        storage.setHome(owner, "base", point(4));
        storage.deleteWarp("shop");
        storage.deleteSpawn(null);
        storage.setHome(owner, "mine", point(5));

        assertEquals(point(1), snapshot.homes().get(owner).get("base"));
        assertEquals(point(2), snapshot.spawn());
        assertEquals(point(3), snapshot.warps().get("shop"));
        assertEquals(Map.of("base", point(1)), homes);
        assertEquals(List.of("base"), new ArrayList<>(homeNames));
        assertEquals(List.of("shop"), new ArrayList<>(warpNames));
        assertEquals(point(2), spawns.get(""));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.homes().get(owner).clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.homes().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.warps().clear());
        assertThrows(UnsupportedOperationException.class, homes::clear);
        assertThrows(UnsupportedOperationException.class, homeNames::clear);
    }

    @Test
    void replaceReusesAnImmutableSnapshotWithoutSharingMutableMaps() {
        MemoryStorage storage = new MemoryStorage();
        UUID owner = UUID.randomUUID();
        PointStorageSnapshot replacement = new PointStorageSnapshot(Map.of("", point(1)),
                Map.of(owner, Map.of("base", point(2))), Map.of("shop", point(3)));
        storage.replace(replacement);
        assertSame(replacement, storage.snapshot());

        storage.setHome(owner, "base", point(4));
        assertEquals(point(2), replacement.homes().get(owner).get("base"));
        assertEquals(point(4), storage.snapshot().homes().get(owner).get("base"));
    }

    @Test
    @Timeout(15)
    void parallelMutationsAndSnapshotsDoNotLoseIndependentPoints() {
        MemoryStorage storage = new MemoryStorage();
        List<CompletableFuture<Void>> writers = new ArrayList<>();
        for (int region = 0; region < 8; region++) {
            int regionId = region;
            writers.add(CompletableFuture.runAsync(() -> {
                UUID owner = new UUID(0, regionId);
                for (int i = 0; i < 100; i++) {
                    storage.setHome(owner, "home" + i, point(i));
                    storage.setWarp("r" + regionId + "warp" + i, point(i));
                    PointStorageSnapshot snapshot = storage.snapshot();
                    assertEquals(point(i), snapshot.homes().get(owner).get("home" + i));
                    assertEquals(point(i), snapshot.warps().get("r" + regionId + "warp" + i));
                }
            }));
        }
        CompletableFuture.allOf(writers.toArray(CompletableFuture[]::new)).join();

        PointStorageSnapshot snapshot = storage.snapshot();
        assertEquals(800, snapshot.warps().size());
        assertEquals(8, snapshot.homes().size());
        snapshot.homes().values().forEach(homes -> assertEquals(100, homes.size()));
    }

    private static PointStorageSnapshot changedSnapshot(MemoryStorage storage, PointStorageSnapshot before) {
        PointStorageSnapshot after = storage.snapshot();
        assertNotSame(before, after);
        assertSame(after, storage.snapshot());
        return after;
    }

    private static StoredPoint point(double x) {
        return StoredPoint.of("world", x, 64, 0, 0, 0, "", "", "", true, "test", 1);
    }

    private static final class MemoryStorage extends AbstractPointStorage {
        @Override
        public void load() {
        }

        @Override
        public void save(PointStorageSnapshot snapshot) {
        }
    }
}
