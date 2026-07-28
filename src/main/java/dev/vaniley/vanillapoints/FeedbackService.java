package dev.vaniley.vanillapoints;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Locale;

final class FeedbackService {
    private final VanillaPoints plugin;

    FeedbackService(VanillaPoints plugin) {
        this.plugin = plugin;
    }

    void play(Player player, String eventKey) {
        String eventPath = "feedback.events." + eventKey;
        if (plugin.getConfig().getBoolean("feedback.sounds", true)) {
            playConfiguredSound(player, eventPath);
        }
        if (plugin.getConfig().getBoolean("feedback.particles", true)) {
            spawnConfiguredParticle(player, eventPath);
        }
    }

    private void playConfiguredSound(Player player, String eventPath) {
        String soundName = plugin.getConfig().getString(eventPath + ".sound", "");
        if (soundName == null || soundName.isBlank()) {
            return;
        }

        NamespacedKey key = soundKey(soundName);
        Sound sound = key == null ? null : Registry.SOUNDS.get(key);
        if (sound == null) {
            plugin.getLogger().warning("Invalid feedback sound '" + soundName + "' at " + eventPath + ".sound");
            return;
        }

        float volume = (float) Math.max(0.0D, plugin.getConfig().getDouble(eventPath + ".volume", 1.0D));
        float pitch = (float) Math.max(0.01D, plugin.getConfig().getDouble(eventPath + ".pitch", 1.0D));
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private NamespacedKey soundKey(String soundName) {
        String normalized = soundName.trim().toLowerCase(Locale.ROOT).replace('_', '.');
        try {
            return normalized.contains(":") ? NamespacedKey.fromString(normalized) : NamespacedKey.minecraft(normalized);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void spawnConfiguredParticle(Player player, String eventPath) {
        String particleName = plugin.getConfig().getString(eventPath + ".particle", "");
        if (particleName == null || particleName.isBlank()) {
            return;
        }

        try {
            Particle particle = Particle.valueOf(particleName.toUpperCase(Locale.ROOT));
            if (particle.getDataType() != Void.class) {
                plugin.getLogger().warning("Feedback particle '" + particleName + "' at " + eventPath
                        + ".particle requires data and was skipped.");
                return;
            }

            int count = Math.max(0, plugin.getConfig().getInt(eventPath + ".count", 8));
            if (count == 0) {
                return;
            }

            Location location = player.getLocation().add(0.0D, 1.0D, 0.0D);
            player.spawnParticle(particle, location, count, 0.35D, 0.45D, 0.35D);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Invalid feedback particle '" + particleName + "' at " + eventPath + ".particle");
        }
    }
}
