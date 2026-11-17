package dev.vaniley.vanillapoints;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PointStorageFactoryTest {
    @TempDir
    Path tempDir;

    @Test
    void closesCandidateAfterLoadFailure() {
        JavaPlugin plugin = plugin("yaml");
        StorageException failure = new StorageException("load failed");
        try (MockedConstruction<YamlPointStorage> candidates = mockConstruction(YamlPointStorage.class,
                (storage, context) -> doThrow(failure).when(storage).load())) {
            assertSame(failure, assertThrows(StorageException.class, () -> PointStorageFactory.create(plugin)));
            verify(candidates.constructed().getFirst()).close();
        }
    }

    @Test
    void keepsTheLoadFailureAndSuppressesCleanupFailure() {
        JavaPlugin plugin = plugin("yaml");
        StorageException failure = new StorageException("load failed");
        StorageException cleanupFailure = new StorageException("close failed");
        try (MockedConstruction<YamlPointStorage> candidates = mockConstruction(YamlPointStorage.class,
                (storage, context) -> {
                    doThrow(failure).when(storage).load();
                    doThrow(cleanupFailure).when(storage).close();
                })) {
            assertSame(failure, assertThrows(StorageException.class, () -> PointStorageFactory.create(plugin)));
            assertEquals(1, failure.getSuppressed().length);
            assertSame(cleanupFailure, failure.getSuppressed()[0]);
            verify(candidates.constructed().getFirst()).close();
        }
    }

    @Test
    void closesCandidateWhenYamlMigrationFails() throws Exception {
        JavaPlugin plugin = plugin("sqlite");
        Files.writeString(tempDir.resolve("data.yml"), "spawn: data");
        PointStorageSnapshot imported = new PointStorageSnapshot(Map.of("", point()), Map.of(), Map.of());
        StorageException failure = new StorageException("migration save failed");
        try (MockedConstruction<SqlitePointStorage> candidates = mockConstruction(SqlitePointStorage.class,
                (storage, context) -> {
                    when(storage.snapshot()).thenReturn(PointStorageSnapshot.empty());
                    doThrow(failure).when(storage).save(any());
                });
             MockedConstruction<YamlPointStorage> yaml = mockConstruction(YamlPointStorage.class,
                     (storage, context) -> when(storage.snapshot()).thenReturn(imported))) {
            assertSame(failure, assertThrows(StorageException.class, () -> PointStorageFactory.create(plugin)));
            verify(candidates.constructed().getFirst()).close();
            verify(yaml.constructed().getFirst()).load();
        }
    }

    @Test
    void successfulCandidateStaysOpen() {
        JavaPlugin plugin = plugin("yaml");
        try (MockedConstruction<YamlPointStorage> candidates = mockConstruction(YamlPointStorage.class)) {
            PointStorage result = PointStorageFactory.create(plugin);
            assertSame(candidates.constructed().getFirst(), result);
            verify(result).load();
            verify(result, never()).close();
        }
    }

    private JavaPlugin plugin(String backend) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("storage.backend", backend);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        return plugin;
    }

    private StoredPoint point() {
        return StoredPoint.of("world", 1, 64, 0, 0, 0, "", "", "", true, "test", 1);
    }
}
