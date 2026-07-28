package dev.vaniley.vanillapoints;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.UUID;

/**
 * Imports homes and warps from an existing EssentialsX installation. Reads {@code plugins/Essentials/userdata/*.yml}
 * for per-player homes and {@code plugins/Essentials/warps/*.yml} for warps, writing them into VanillaPoints storage.
 * Existing VanillaPoints points with the same name are overwritten.
 */
final class EssentialsImporter {
    private final JavaPlugin plugin;
    private final PointStorage storage;

    EssentialsImporter(JavaPlugin plugin, PointStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    Result importData() {
        File essentials = new File(plugin.getDataFolder().getParentFile(), "Essentials");
        if (!essentials.isDirectory()) {
            plugin.getLogger().warning("Essentials import requested but no Essentials folder was found.");
            return new Result(0, 0);
        }

        return new Result(importHomes(new File(essentials, "userdata")), importWarps(new File(essentials, "warps")));
    }

    private int importHomes(File userdata) {
        if (!userdata.isDirectory()) {
            return 0;
        }

        int imported = 0;
        File[] files = userdata.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null) {
            return 0;
        }

        for (File file : files) {
            UUID playerId = parseUuid(file.getName());
            if (playerId == null) {
                continue;
            }

            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection homes = config.getConfigurationSection("homes");
            if (homes == null) {
                continue;
            }

            for (String rawName : homes.getKeys(false)) {
                ConfigurationSection section = homes.getConfigurationSection(rawName);
                StoredPoint point = readPoint(section);
                if (point == null) {
                    continue;
                }
                String name = sanitizeName(rawName);
                if (!PointStorage.isValidHomeName(name)) {
                    plugin.getLogger().warning("Skipping Essentials home with unsupported name: " + rawName);
                    continue;
                }
                storage.setHome(playerId, PointStorage.normalizeHomeName(name), point);
                imported++;
            }
        }
        return imported;
    }

    private int importWarps(File warps) {
        if (!warps.isDirectory()) {
            return 0;
        }

        int imported = 0;
        File[] files = warps.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null) {
            return 0;
        }

        for (File file : files) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            StoredPoint point = readPoint(config);
            if (point == null) {
                continue;
            }
            String rawName = file.getName().substring(0, file.getName().length() - ".yml".length());
            String name = sanitizeName(rawName);
            if (!PointStorage.isValidWarpName(name)) {
                plugin.getLogger().warning("Skipping Essentials warp with unsupported name: " + rawName);
                continue;
            }
            storage.setWarp(PointStorage.normalizeWarpName(name), point);
            imported++;
        }
        return imported;
    }

    private StoredPoint readPoint(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String world = section.getString("world-name", section.getString("world"));
        if (world == null || world.isBlank() || !section.isDouble("x") && !section.isInt("x")) {
            return null;
        }

        return StoredPoint.of(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch"),
                "",
                "",
                "",
                true,
                "Essentials",
                0L
        );
    }

    private String sanitizeName(String name) {
        return name == null ? "" : name.replaceAll("[^A-Za-z0-9_-]", "");
    }

    private UUID parseUuid(String fileName) {
        if (!fileName.toLowerCase().endsWith(".yml")) {
            return null;
        }
        try {
            return UUID.fromString(fileName.substring(0, fileName.length() - ".yml".length()));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    record Result(int homes, int warps) {
    }
}
