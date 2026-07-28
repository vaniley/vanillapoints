package dev.vaniley.vanillapoints;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Releases per-player transient state (cooldowns, rate-limit windows, pending confirmations) when a player
 * leaves, so the in-memory maps do not grow on servers with high player turnover.
 */
final class PlayerStateListener implements Listener {
    private final VanillaPoints plugin;

    PlayerStateListener(VanillaPoints plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.clearPlayerState(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.notifyUpdate(event.getPlayer());
    }
}
