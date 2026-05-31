package dev.vaniley.vanillapoints;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

final class MessageService {
    private final JavaPlugin plugin;
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();
    private FileConfiguration messages;

    MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void load() {
        String language = plugin.getConfig().getString("settings.language", "en");
        String fileName = messageFileName(language);
        if (!hasBundledResource(fileName)) {
            plugin.getLogger().warning("Unknown message language '" + language + "', falling back to English.");
            fileName = "messages.yml";
        }

        File messagesFile = new File(plugin.getDataFolder(), fileName);
        if (!messagesFile.exists()) {
            plugin.saveResource(fileName, false);
        }

        messages = YamlConfiguration.loadConfiguration(messagesFile);
        loadDefaults();
    }

    private boolean hasBundledResource(String fileName) {
        try (InputStream inputStream = plugin.getResource(fileName)) {
            return inputStream != null;
        } catch (Exception exception) {
            return false;
        }
    }

    private String messageFileName(String language) {
        if (language == null || language.isBlank() || language.equalsIgnoreCase("en")) {
            return "messages.yml";
        }

        return "messages_" + language.toLowerCase() + ".yml";
    }

    private void loadDefaults() {
        try (InputStream inputStream = plugin.getResource("messages.yml")) {
            if (inputStream == null) {
                return;
            }

            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8)
            );
            messages.setDefaults(defaults);
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not load default messages: " + exception.getMessage());
        }
    }

    Component component(String key) {
        return component(key, Map.of());
    }

    Component component(String key, Map<String, String> placeholders) {
        return legacySerializer.deserialize(text(key, placeholders));
    }

    String text(String key) {
        return text(key, Map.of());
    }

    String text(String key, Map<String, String> placeholders) {
        String message = messages.getString(key);
        if (message == null) {
            return "&cMessage not found: " + key;
        }

        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            message = message.replace("{" + placeholder.getKey() + "}", placeholder.getValue());
        }
        return message;
    }
}
