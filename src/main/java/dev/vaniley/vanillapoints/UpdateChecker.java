package dev.vaniley.vanillapoints;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Lightweight, optional update checker. It fetches a plain-text "latest version" string from a configurable
 * endpoint (SpigotMC legacy API by resource id, or any custom URL) and compares it to the running version.
 * Networking happens off the main thread; notifications are scheduled back onto it.
 */
final class UpdateChecker {
    private static final String SPIGOT_API = "https://api.spigotmc.org/legacy/update.php?resource=";
    private static final String NOTIFY_PERMISSION = "vanillapoints.update.notify";

    private final VanillaPoints plugin;
    private volatile String latestVersion;
    private volatile boolean updateAvailable;

    UpdateChecker(VanillaPoints plugin) {
        this.plugin = plugin;
    }

    void checkAsync() {
        if (!plugin.getConfig().getBoolean("update-checker.enabled", true)) {
            return;
        }

        String url = resolveUrl();
        if (url == null) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String remote = fetch(url);
            if (remote == null || remote.isBlank()) {
                return;
            }

            String current = plugin.getPluginMeta().getVersion();
            latestVersion = remote.trim();
            if (!isNewer(latestVersion, current)) {
                return;
            }

            updateAvailable = true;
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> plugin.getLogger().info(
                        "A new VanillaPoints version is available: " + latestVersion + " (running " + current + ")."));
            }
        });
    }

    boolean isUpdateAvailable() {
        return updateAvailable;
    }

    String latestVersion() {
        return latestVersion;
    }

    void notifyIfAdmin(Player player) {
        if (updateAvailable && player.hasPermission(NOTIFY_PERMISSION)) {
            player.sendMessage(plugin.messages().component(player, "update-available", java.util.Map.of(
                    "version", latestVersion == null ? "?" : latestVersion
            )));
        }
    }

    private String resolveUrl() {
        int resourceId = plugin.getConfig().getInt("update-checker.spigot-resource-id", 0);
        if (resourceId > 0) {
            return SPIGOT_API + resourceId;
        }
        String custom = plugin.getConfig().getString("update-checker.url", "");
        return custom == null || custom.isBlank() ? null : custom.trim();
    }

    private String fetch(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(5_000);
            connection.setRequestProperty("User-Agent", "VanillaPoints/" + plugin.getPluginMeta().getVersion());
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                return reader.readLine();
            }
        } catch (Exception exception) {
            plugin.getLogger().log(Level.FINE, "Update check failed: " + exception.getMessage());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Compares dotted numeric version strings (e.g. {@code 1.3.0}). Non-numeric segments are treated as 0.
     */
    static boolean isNewer(String candidate, String current) {
        int[] a = parse(candidate);
        int[] b = parse(current);
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int left = i < a.length ? a[i] : 0;
            int right = i < b.length ? b[i] : 0;
            if (left != right) {
                return left > right;
            }
        }
        return false;
    }

    private static int[] parse(String version) {
        String cleaned = version.replaceAll("[^0-9.].*$", "");
        String[] parts = cleaned.isBlank() ? new String[0] : cleaned.split("\\.");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                numbers[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException exception) {
                numbers[i] = 0;
            }
        }
        return numbers;
    }
}
