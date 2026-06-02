package dev.vaniley.vanillapoints;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Locale;

final class PointStorageFactory {
    private PointStorageFactory() {
    }

    static PointStorage create(JavaPlugin plugin) throws StorageException {
        String backend = plugin.getConfig().getString("storage.backend", "yaml");
        String normalizedBackend = backend == null ? "yaml" : backend.trim().toLowerCase(Locale.ROOT);

        PointStorage storage = switch (normalizedBackend) {
            case "yaml" -> new YamlPointStorage(plugin);
            case "sqlite" -> new SqlitePointStorage(plugin);
            case "mysql" -> new MysqlPointStorage(plugin);
            default -> throw new StorageException("Unknown storage backend: " + backend);
        };

        storage.load();
        if (!normalizedBackend.equals("yaml") && plugin.getConfig().getBoolean("storage.migrate-yaml-on-first-run", true)) {
            migrateYamlIfNeeded(plugin, storage);
        }
        return storage;
    }

    private static void migrateYamlIfNeeded(JavaPlugin plugin, PointStorage targetStorage) {
        if (!targetStorage.snapshot().isEmpty()) {
            return;
        }

        File dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists() || dataFile.length() == 0L) {
            return;
        }

        YamlPointStorage yamlStorage = new YamlPointStorage(plugin);
        yamlStorage.load();
        PointStorageSnapshot snapshot = yamlStorage.snapshot();
        if (snapshot.isEmpty()) {
            return;
        }

        targetStorage.replace(snapshot);
        targetStorage.save(snapshot);
        plugin.getLogger().info("Migrated VanillaPoints YAML data to selected storage backend: "
                + snapshot.homes().size() + " homes, " + snapshot.warps().size() + " warps"
                + (snapshot.spawn() == null ? ", no spawn." : ", spawn included."));
    }
}
