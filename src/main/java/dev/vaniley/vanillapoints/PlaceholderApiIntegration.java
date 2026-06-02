package dev.vaniley.vanillapoints;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class PlaceholderApiIntegration extends PlaceholderExpansion {
    private static final List<String> WARP_FIELDS = List.of("description", "world", "icon", "set", "x", "y", "z");

    private final VanillaPoints plugin;
    private final PointService points;

    PlaceholderApiIntegration(VanillaPoints plugin, PointService points) {
        this.plugin = plugin;
        this.points = points;
    }

    @Override
    public String getIdentifier() {
        return "vanillapoints";
    }

    @Override
    public String getAuthor() {
        List<String> authors = plugin.getDescription().getAuthors();
        return authors.isEmpty() ? "VanillaPoints" : String.join(", ", authors);
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        return request(player, player != null && player.isOnline() ? player.getPlayer() : null, params);
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        return request(player, player, params);
    }

    private String request(OfflinePlayer offlinePlayer, Player player, String params) {
        if (params == null || params.isBlank()) {
            return emptyValue();
        }

        String normalizedParams = params.toLowerCase(Locale.ROOT);
        if (normalizedParams.startsWith("spawn_")) {
            return spawnPlaceholder(normalizedParams.substring("spawn_".length()));
        }
        if (normalizedParams.startsWith("home_")) {
            return homePlaceholder(offlinePlayer, normalizedParams.substring("home_".length()));
        }
        if (normalizedParams.startsWith("warp_")) {
            return warpPlaceholder(normalizedParams.substring("warp_".length()));
        }
        if (normalizedParams.equals("distance_home")) {
            return distanceToHome(player);
        }
        if (normalizedParams.equals("bearing_home")) {
            return bearingToHome(player);
        }

        return emptyValue();
    }

    private String spawnPlaceholder(String field) {
        Optional<StoredPoint> spawn = points.spawn();
        if (field.equals("set")) {
            return spawn.isPresent() ? "true" : "false";
        }
        return spawn.map(point -> pointField(point, field)).orElseGet(this::emptyValue);
    }

    private String homePlaceholder(OfflinePlayer player, String field) {
        if (player == null) {
            return emptyValue();
        }

        Optional<StoredPoint> home = points.home(player.getUniqueId());
        if (field.equals("set")) {
            return home.isPresent() ? "true" : "false";
        }
        return home.map(point -> pointField(point, field)).orElseGet(this::emptyValue);
    }

    private String warpPlaceholder(String body) {
        if (body.equals("count")) {
            return String.valueOf(points.warpNames().size());
        }
        if (body.equals("list")) {
            return String.join(plugin.getConfig().getString("placeholders.warp-list-separator", ", "), points.warpNames());
        }

        for (String field : WARP_FIELDS) {
            String suffix = "_" + field;
            if (!body.endsWith(suffix)) {
                continue;
            }

            String warpName = body.substring(0, body.length() - suffix.length());
            if (!PointStorage.isValidWarpName(warpName)) {
                return emptyValue();
            }

            Optional<StoredPoint> warp = points.warp(warpName);
            if (field.equals("set")) {
                return warp.isPresent() ? "true" : "false";
            }
            return warp.map(point -> pointField(point, field)).orElseGet(this::emptyValue);
        }

        return emptyValue();
    }

    private String pointField(StoredPoint point, String field) {
        return switch (field) {
            case "x" -> String.valueOf(point.blockX());
            case "y" -> String.valueOf(point.blockY());
            case "z" -> String.valueOf(point.blockZ());
            case "world" -> point.worldName();
            case "description" -> valueOrEmpty(point.description());
            case "icon" -> valueOrEmpty(point.icon());
            default -> emptyValue();
        };
    }

    private String distanceToHome(Player player) {
        if (player == null) {
            return emptyValue();
        }

        Optional<StoredPoint> home = points.home(player.getUniqueId());
        if (home.isEmpty() || !sameWorld(player, home.get())) {
            return emptyValue();
        }

        Location location = player.getLocation();
        StoredPoint point = home.get();
        double dx = point.x() - location.getX();
        double dy = point.y() - location.getY();
        double dz = point.z() - location.getZ();
        return String.format(Locale.ROOT, "%.1f", Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    private String bearingToHome(Player player) {
        if (player == null) {
            return emptyValue();
        }

        Optional<StoredPoint> home = points.home(player.getUniqueId());
        if (home.isEmpty() || !sameWorld(player, home.get())) {
            return emptyValue();
        }

        Location location = player.getLocation();
        double dx = home.get().x() - location.getX();
        double dz = home.get().z() - location.getZ();
        if (Math.abs(dx) < 0.001D && Math.abs(dz) < 0.001D) {
            return "here";
        }

        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        if (angle < 0.0D) {
            angle += 360.0D;
        }
        return compassDirection(angle);
    }

    private boolean sameWorld(Player player, StoredPoint point) {
        return player.getWorld().getName().equals(point.worldName());
    }

    private String compassDirection(double angle) {
        String[] directions = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int index = (int) Math.round(angle / 45.0D) % directions.length;
        return directions[index];
    }

    private String valueOrEmpty(String value) {
        return value == null || value.isBlank() ? emptyValue() : value;
    }

    private String emptyValue() {
        return plugin.getConfig().getString("placeholders.empty-value", "");
    }
}
