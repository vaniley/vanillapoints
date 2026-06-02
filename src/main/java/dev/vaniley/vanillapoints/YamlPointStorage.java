package dev.vaniley.vanillapoints;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class YamlPointStorage extends AbstractPointStorage {
    private final JavaPlugin plugin;
    private File dataFile;

    YamlPointStorage(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void load() throws StorageException {
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            plugin.saveResource("data.yml", false);
        }

        FileConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        replace(readSnapshot(data));
    }

    @Override
    public void save(PointStorageSnapshot snapshot) throws StorageException {
        ensureDataFile();

        YamlConfiguration data = new YamlConfiguration();
        if (snapshot.spawn() != null) {
            savePoint(data, "spawn", snapshot.spawn());
        }
        snapshot.homes().forEach((uuid, point) -> savePoint(data, "homes." + uuid, point));
        snapshot.warps().forEach((name, point) -> savePoint(data, "warps." + name, point));

        try {
            saveSafely(data);
        } catch (IOException exception) {
            throw new StorageException("Could not save data.yml", exception);
        }
    }

    private PointStorageSnapshot readSnapshot(FileConfiguration data) {
        StoredPoint spawn = readPoint(data, "spawn").orElse(null);
        Map<UUID, StoredPoint> homes = loadHomes(data);
        Map<String, StoredPoint> warps = loadWarps(data);
        return new PointStorageSnapshot(spawn, homes, warps);
    }

    private Map<UUID, StoredPoint> loadHomes(FileConfiguration data) {
        Map<UUID, StoredPoint> homes = new HashMap<>();
        ConfigurationSection section = data.getConfigurationSection("homes");
        if (section == null) {
            return homes;
        }

        for (String key : section.getKeys(false)) {
            try {
                readPoint(data, "homes." + key).ifPresent(point -> homes.put(UUID.fromString(key), point));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Skipping home with invalid UUID: " + key);
            }
        }
        return homes;
    }

    private Map<String, StoredPoint> loadWarps(FileConfiguration data) {
        Map<String, StoredPoint> warps = new HashMap<>();
        ConfigurationSection section = data.getConfigurationSection("warps");
        if (section == null) {
            return warps;
        }

        for (String key : section.getKeys(false)) {
            if (!PointStorage.isValidWarpName(key)) {
                plugin.getLogger().warning("Skipping warp with invalid name: " + key);
                continue;
            }

            readPoint(data, "warps." + key).ifPresent(point -> warps.put(PointStorage.normalizeWarpName(key), point));
        }
        return warps;
    }

    private Optional<StoredPoint> readPoint(FileConfiguration data, String path) {
        ConfigurationSection section = data.getConfigurationSection(path);
        if (section == null) {
            return Optional.empty();
        }

        StoredPoint point = StoredPoint.fromSection(section);
        if (point == null) {
            plugin.getLogger().warning("Skipping invalid point at " + path);
            return Optional.empty();
        }
        return Optional.of(point);
    }

    private void ensureDataFile() {
        if (dataFile == null) {
            dataFile = new File(plugin.getDataFolder(), "data.yml");
        }
    }

    private void saveSafely(YamlConfiguration data) throws IOException {
        Path dataPath = dataFile.toPath();
        Path backupPath = dataPath.resolveSibling(dataFile.getName() + ".bak");
        Path temporaryPath = dataPath.resolveSibling(dataFile.getName() + ".tmp");

        data.save(temporaryPath.toFile());
        if (Files.exists(dataPath)) {
            Files.copy(dataPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(temporaryPath, dataPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            Files.move(temporaryPath, dataPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void savePoint(YamlConfiguration data, String path, StoredPoint point) {
        ConfigurationSection section = data.createSection(path);
        point.save(section);
    }
}
