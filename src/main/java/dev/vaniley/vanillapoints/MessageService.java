package dev.vaniley.vanillapoints;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class MessageService {
    static final String DEFAULT_LANGUAGE = "en";
    static final List<String> SUPPORTED_LANGUAGES = List.of("en", "ru", "uk", "es", "de", "fr", "zh", "ja", "pt", "pl");

    private final JavaPlugin plugin;
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();
    private final Map<String, FileConfiguration> loadedMessages = new HashMap<>();
    private FileConfiguration defaultMessages;
    private String globalLanguage = DEFAULT_LANGUAGE;

    MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void load() {
        loadedMessages.clear();
        defaultMessages = loadBundledDefaults();

        String configuredLanguage = normalizeLanguage(plugin.getConfig().getString("settings.language", DEFAULT_LANGUAGE));
        if (!isSupportedLanguage(configuredLanguage)) {
            plugin.getLogger().warning("Unknown message language '" + configuredLanguage + "', falling back to English.");
            configuredLanguage = DEFAULT_LANGUAGE;
        }
        globalLanguage = configuredLanguage;
        loadLanguage(globalLanguage);
    }

    private boolean hasBundledResource(String fileName) {
        try (InputStream inputStream = plugin.getResource(fileName)) {
            return inputStream != null;
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean isSupportedLanguage(String language) {
        return SUPPORTED_LANGUAGES.contains(language) && hasBundledResource(messageFileName(language));
    }

    private String messageFileName(String language) {
        if (language == null || language.isBlank() || language.equalsIgnoreCase(DEFAULT_LANGUAGE)) {
            return "messages.yml";
        }

        return "messages_" + language.toLowerCase(Locale.ROOT) + ".yml";
    }

    private FileConfiguration loadBundledDefaults() {
        try (InputStream inputStream = plugin.getResource("messages.yml")) {
            if (inputStream == null) {
                return new YamlConfiguration();
            }

            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8)
            );
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not load default messages: " + exception.getMessage());
            return new YamlConfiguration();
        }
    }

    private FileConfiguration loadLanguage(String language) {
        String normalizedLanguage = normalizeLanguage(language);
        return loadedMessages.computeIfAbsent(normalizedLanguage, this::loadLanguageFile);
    }

    private FileConfiguration loadLanguageFile(String language) {
        String fileName = messageFileName(language);
        File messagesFile = new File(plugin.getDataFolder(), fileName);
        if (!messagesFile.exists()) {
            plugin.saveResource(fileName, false);
        }

        FileConfiguration messages = YamlConfiguration.loadConfiguration(messagesFile);
        messages.setDefaults(defaultMessages);
        return messages;
    }

    private String languageFor(CommandSender sender) {
        if (!(sender instanceof Player player) || !plugin.getConfig().getBoolean("settings.per-player-permissions", false)) {
            return globalLanguage;
        }

        for (String language : languageSelectionOrder()) {
            if (player.hasPermission("vanillapoints.lang." + language)) {
                return language;
            }
        }
        return globalLanguage;
    }

    private List<String> languageSelectionOrder() {
        if (DEFAULT_LANGUAGE.equals(globalLanguage)) {
            return SUPPORTED_LANGUAGES;
        }

        return SUPPORTED_LANGUAGES.stream()
                .sorted((first, second) -> {
                    if (first.equals(globalLanguage)) {
                        return -1;
                    }
                    if (second.equals(globalLanguage)) {
                        return 1;
                    }
                    return 0;
                })
                .toList();
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return DEFAULT_LANGUAGE;
        }
        return language.trim().toLowerCase(Locale.ROOT);
    }

    Component component(String key) {
        return component(key, Map.of());
    }

    Component component(String key, Map<String, String> placeholders) {
        return legacySerializer.deserialize(text(key, placeholders));
    }

    Component component(CommandSender sender, String key) {
        return component(sender, key, Map.of());
    }

    Component component(CommandSender sender, String key, Map<String, String> placeholders) {
        return legacySerializer.deserialize(text(sender, key, placeholders));
    }

    String text(String key) {
        return text(key, Map.of());
    }

    String text(String key, Map<String, String> placeholders) {
        return text(loadLanguage(globalLanguage), key, placeholders);
    }

    String text(CommandSender sender, String key) {
        return text(sender, key, Map.of());
    }

    String text(CommandSender sender, String key, Map<String, String> placeholders) {
        return text(loadLanguage(languageFor(sender)), key, placeholders);
    }

    private String text(FileConfiguration messages, String key, Map<String, String> placeholders) {
        String message = messages.getString(key);
        if (message == null) {
            return "&cMessage not found: " + key;
        }

        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            message = message.replace("{" + placeholder.getKey() + "}", placeholder.getValue() == null ? "" : placeholder.getValue());
        }
        return message;
    }
}
