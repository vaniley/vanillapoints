package dev.vaniley.vanillapoints;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

final class PointStorage {
    private static final Pattern WARP_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    private final JavaPlugin plugin;
    private final boolean normalizeToBlock;
    private final Map<UUID, StoredPoint> homes = new HashMap<>();
    private final Map<String, StoredPoint> warps = new HashMap<>();
    private File dataFile;
    private FileConfiguration data;
    private StoredPoint spawn;

    PointStorage(JavaPlugin plugin, boolean normalizeToBlock) {
        this.plugin = plugin;
        this.normalizeToBlock = normalizeToBlock;
    }

    void load() {
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            plugin.saveResource("data.yml", false);
        }

        data = YamlConfiguration.loadConfiguration(dataFile);
        spawn = readPoint("spawn").orElse(null);
        loadHomes();
        loadWarps();
    }

    private void loadHomes() {
        homes.clear();
        ConfigurationSection section = data.getConfigurationSection("homes");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            try {
                readPoint("homes." + key).ifPresent(point -> homes.put(UUID.fromString(key), point));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Skipping home with invalid UUID: " + key);
            }
        }
    }

    private void loadWarps() {
        warps.clear();
        ConfigurationSection section = data.getConfigurationSection("warps");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            if (!isValidWarpName(key)) {
                plugin.getLogger().warning("Skipping warp with invalid name: " + key);
                continue;
            }

            readPoint("warps." + key).ifPresent(point -> warps.put(normalizeWarpName(key), point));
        }
    }

    private Optional<StoredPoint> readPoint(String path) {
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

    void save() throws IOException {
        data.set("spawn", null);
        data.set("homes", null);
        data.set("warps", null);

        if (spawn != null) {
            savePoint("spawn", spawn);
        }
        homes.forEach((uuid, point) -> savePoint("homes." + uuid, point));
        warps.forEach((name, point) -> savePoint("warps." + name, point));
        saveSafely();
    }

    private void saveSafely() throws IOException {
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

    private void savePoint(String path, StoredPoint point) {
        ConfigurationSection section = data.createSection(path);
        point.save(section);
    }

    void setSpawn(Location location) {
        spawn = StoredPoint.fromLocation(location, normalizeToBlock);
    }

    Optional<StoredPoint> spawn() {
        return Optional.ofNullable(spawn);
    }

    void setHome(UUID playerId, Location location) {
        homes.put(playerId, StoredPoint.fromLocation(location, normalizeToBlock));
    }

    Optional<StoredPoint> home(UUID playerId) {
        return Optional.ofNullable(homes.get(playerId));
    }

    void setWarp(String name, Location location) {
        warps.put(normalizeWarpName(name), StoredPoint.fromLocation(location, normalizeToBlock));
    }

    Optional<StoredPoint> warp(String name) {
        return Optional.ofNullable(warps.get(normalizeWarpName(name)));
    }

    boolean deleteWarp(String name) {
        return warps.remove(normalizeWarpName(name)) != null;
    }

    Set<String> warpNames() {
        return Collections.unmodifiableSet(new TreeSet<>(warps.keySet()));
    }

    static String normalizeWarpName(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    static boolean isValidWarpName(String name) {
        return WARP_NAME_PATTERN.matcher(name).matches();
    }
}
