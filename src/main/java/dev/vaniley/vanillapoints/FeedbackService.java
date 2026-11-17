package dev.vaniley.vanillapoints;

import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Validates feedback once per reload, not on each player command. */
final class FeedbackService {
    private final Map<String, Feedback> events;

    FeedbackService(VanillaPoints plugin) {
        Map<String, Feedback> configured = new HashMap<>();
        var config = plugin.getConfig();
        for (String event : new String[]{"home-set", "warp-set", "spawn-set"}) {
            String path = "feedback.events." + event;
            Sound sound = null;
            String soundName = config.getString(path + ".sound", "");
            if (config.getBoolean("feedback.sounds", true) && soundName != null && !soundName.isBlank()) {
                NamespacedKey key = soundKey(soundName);
                sound = key == null ? null : Registry.SOUNDS.get(key);
                if (sound == null && !soundName.contains(":")) {
                    // Flattening underscores loses word boundaries (power_select, note_block).
                    for (Sound candidate : Registry.SOUNDS) {
                        if (matchesLegacySound(soundName, candidate.getKey())) {
                            sound = candidate;
                            break;
                        }
                    }
                }
                if (sound == null) {
                    plugin.getLogger().warning("Invalid feedback sound '" + soundName + "' at " + path + ".sound");
                }
            }
            Particle particle = null;
            String particleName = config.getString(path + ".particle", "");
            if (config.getBoolean("feedback.particles", true) && particleName != null && !particleName.isBlank()) {
                try {
                    particle = Particle.valueOf(particleName.toUpperCase(Locale.ROOT));
                    if (particle.getDataType() != Void.class) {
                        particle = null;
                    }
                } catch (IllegalArgumentException ignored) {
                    // Invalid names and data-dependent particles cannot be used for simple feedback.
                }
                if (particle == null) {
                    plugin.getLogger().warning("Invalid or data-dependent feedback particle '" + particleName + "' at " + path + ".particle");
                }
            }
            configured.put(event, new Feedback(sound, particle,
                    bounded(config.getDouble(path + ".volume", 1.0), 0.0, 16.0, 1.0),
                    bounded(config.getDouble(path + ".pitch", 1.0), 0.01, 2.0, 1.0),
                    Math.clamp(config.getInt(path + ".count", 8), 0, 256)));
        }
        events = Map.copyOf(configured);
    }

    void play(Player player, String eventKey) {
        Feedback feedback = events.get(eventKey);
        if (feedback == null) {
            return;
        }
        var location = player.getLocation();
        if (feedback.sound() != null) {
            player.playSound(location, feedback.sound(), feedback.volume(), feedback.pitch());
        }
        if (feedback.particle() != null && feedback.count() > 0) {
            player.spawnParticle(feedback.particle(), location.add(0.0, 1.0, 0.0),
                    feedback.count(), 0.35, 0.45, 0.35);
        }
    }

    static NamespacedKey soundKey(String soundName) {
        String normalized = soundName.trim().toLowerCase(Locale.ROOT);
        // Legacy enum names use underscores; namespaced registry keys can contain literal underscores.
        if (!normalized.contains(":")) {
            normalized = normalized.replace('_', '.');
        }
        return NamespacedKey.fromString(normalized);
    }

    static boolean matchesLegacySound(String name, NamespacedKey key) {
        return key.getNamespace().equals(NamespacedKey.MINECRAFT)
                && key.getKey().replace('.', '_').equalsIgnoreCase(name.trim());
    }

    static float bounded(double value, double minimum, double maximum, double fallback) {
        return (float) (Double.isFinite(value) ? Math.clamp(value, minimum, maximum) : fallback);
    }

    private record Feedback(Sound sound, Particle particle, float volume, float pitch, int count) {
    }
}
