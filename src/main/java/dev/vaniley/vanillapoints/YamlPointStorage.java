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
        snapshot.spawns().forEach((world, point) -> {
            if (world == null || world.isEmpty()) {
                savePoint(data, "spawn", point);
            } else {
                savePoint(data, "spawns." + world, point);
            }
        });
        snapshot.homes().forEach((uuid, playerHomes) -> playerHomes.forEach((name, point) -> savePoint(data, "homes." + uuid + "." + name, point)));
        snapshot.warps().forEach((name, point) -> savePoint(data, "warps." + name, point));

        try {
            saveSafely(data);
        } catch (IOException exception) {
            throw new StorageException("Could not save data.yml", exception);
        }
    }

    private PointStorageSnapshot readSnapshot(FileConfiguration data) {
        Map<String, StoredPoint> spawns = loadSpawns(data);
        Map<UUID, Map<String, StoredPoint>> homes = loadHomes(data);
        Map<String, StoredPoint> warps = loadWarps(data);
        return new PointStorageSnapshot(spawns, homes, warps);
    }

    private Map<String, StoredPoint> loadSpawns(FileConfiguration data) {
        Map<String, StoredPoint> spawns = new HashMap<>();
        readPoint(data, "spawn").ifPresent(point -> spawns.put(PointStorageSnapshot.GLOBAL_SPAWN_KEY, point));

        ConfigurationSection section = data.getConfigurationSection("spawns");
        if (section != null) {
            for (String world : section.getKeys(false)) {
                readPoint(data, "spawns." + world).ifPresent(point -> spawns.put(world, point));
            }
        }
        return spawns;
    }

    private Map<UUID, Map<String, StoredPoint>> loadHomes(FileConfiguration data) {
        Map<UUID, Map<String, StoredPoint>> homes = new HashMap<>();
        ConfigurationSection section = data.getConfigurationSection("homes");
        if (section == null) {
            return homes;
        }

        for (String key : section.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(key);
                ConfigurationSection playerSection = data.getConfigurationSection("homes." + key);
                if (playerSection == null) {
                    continue;
                }
                if (playerSection.contains("world")) {
                    readPoint(data, "homes." + key).ifPresent(point -> putHome(homes, playerId, PointStorage.DEFAULT_HOME_NAME, point));
                    continue;
                }

                for (String homeName : playerSection.getKeys(false)) {
                    if (!PointStorage.isValidHomeName(homeName)) {
                        plugin.getLogger().warning("Skipping home with invalid name: " + homeName);
                        continue;
                    }
                    readPoint(data, "homes." + key + "." + homeName)
                            .ifPresent(point -> putHome(homes, playerId, PointStorage.normalizeHomeName(homeName), point));
                }
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Skipping home with invalid UUID: " + key);
            }
        }
        return homes;
    }

    private void putHome(Map<UUID, Map<String, StoredPoint>> homes, UUID playerId, String name, StoredPoint point) {
        homes.computeIfAbsent(playerId, ignored -> new HashMap<>()).put(name, point);
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
