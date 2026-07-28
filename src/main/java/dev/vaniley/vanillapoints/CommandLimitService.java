package dev.vaniley.vanillapoints;

import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

final class CommandLimitService {
    private static final String DEFAULT_BYPASS_PERMISSION = "vanillapoints.bypass.cooldown";

    private final VanillaPoints plugin;
    private final MessageService messages;
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Deque<Long>> commandWindows = new HashMap<>();

    CommandLimitService(VanillaPoints plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    boolean check(Player player, String commandName) {
        String bypassPermission = plugin.getConfig().getString("cooldowns.bypass-permission", DEFAULT_BYPASS_PERMISSION);
        if (bypassPermission != null && !bypassPermission.isBlank() && player.hasPermission(bypassPermission)) {
            return true;
        }

        long now = System.currentTimeMillis();
        if (!checkRateLimit(player, now)) {
            return false;
        }

        long cooldownMillis = cooldownMillis(commandName);
        if (cooldownMillis > 0L) {
            Long lastUsedAt = cooldowns.getOrDefault(player.getUniqueId(), Map.of()).get(commandName);
            if (lastUsedAt != null) {
                long remainingMillis = cooldownMillis - (now - lastUsedAt);
                if (remainingMillis > 0L) {
                    player.sendMessage(messages.component(player, "cooldown-active", Map.of(
                            "time", Durations.format(remainingMillis))));
                    return false;
                }
            }
        }

        recordUsage(player.getUniqueId(), commandName, now, cooldownMillis);
        return true;
    }

    void clear(UUID playerId) {
        cooldowns.remove(playerId);
        commandWindows.remove(playerId);
    }

    private boolean checkRateLimit(Player player, long now) {
        long windowMillis = duration("rate-limit.window", "60s");
        int maxCommands = plugin.getConfig().getInt("rate-limit.max-commands", 30);
        if (windowMillis <= 0L || maxCommands <= 0) {
            return true;
        }

        Deque<Long> window = commandWindows.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>());
        pruneWindow(window, now, windowMillis);
        if (window.size() >= maxCommands) {
            Long oldest = window.peekFirst();
            long remaining = oldest == null ? windowMillis : oldest + windowMillis - now;
            player.sendMessage(messages.component(player, "rate-limit-active", Map.of(
                    "time", Durations.format(Math.max(1L, remaining)))));
            return false;
        }
        return true;
    }

    private void recordUsage(UUID playerId, String commandName, long now, long cooldownMillis) {
        long windowMillis = duration("rate-limit.window", "60s");
        int maxCommands = plugin.getConfig().getInt("rate-limit.max-commands", 30);
        if (windowMillis > 0L && maxCommands > 0) {
            commandWindows.computeIfAbsent(playerId, ignored -> new ArrayDeque<>()).addLast(now);
        }
        if (cooldownMillis > 0L) {
            cooldowns.computeIfAbsent(playerId, ignored -> new HashMap<>()).put(commandName, now);
        }
        prune(playerId, now, windowMillis);
    }

    private void prune(UUID playerId, long now, long windowMillis) {
        Deque<Long> window = commandWindows.get(playerId);
        if (window != null) {
            pruneWindow(window, now, windowMillis);
            if (window.isEmpty()) {
                commandWindows.remove(playerId);
            }
        }

        Map<String, Long> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns != null) {
            playerCooldowns.entrySet().removeIf(entry -> now - entry.getValue() >= cooldownMillis(entry.getKey()));
            if (playerCooldowns.isEmpty()) {
                cooldowns.remove(playerId);
            }
        }
    }

    private void pruneWindow(Deque<Long> window, long now, long windowMillis) {
        while (!window.isEmpty() && now - window.peekFirst() >= windowMillis) {
            window.removeFirst();
        }
    }

    private long cooldownMillis(String commandName) {
        long fallback = duration("cooldowns.default", "2s");
        return duration("cooldowns.per-command." + commandName, fallback);
    }

    private long duration(String path, String fallback) {
        OptionalLong parsedFallback = Durations.parse(fallback);
        return duration(path, parsedFallback.orElse(0L));
    }

    private long duration(String path, long fallback) {
        OptionalLong parsed = Durations.parse(plugin.getConfig().get(path));
        return parsed.orElse(fallback);
    }
}
