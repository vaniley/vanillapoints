package dev.vaniley.vanillapoints;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RuntimeConfigurationTest {
    @Test
    void preservesNamespacedSoundKeysAndSupportsLegacyNames() {
        assertEquals("minecraft:entity.player.levelup", FeedbackService.soundKey("ENTITY_PLAYER_LEVELUP").toString());
        assertEquals("minecraft:block.note_block.harp", FeedbackService.soundKey("minecraft:block.note_block.harp").toString());
        assertNull(FeedbackService.soundKey("invalid key"));
    }

    @Test
    void legacySoundAliasesPreserveMultiwordRegistryNames() {
        assertTrue(FeedbackService.matchesLegacySound("BLOCK_BEACON_POWER_SELECT",
                org.bukkit.NamespacedKey.minecraft("block.beacon.power_select")));
        assertTrue(FeedbackService.matchesLegacySound(" block_note_block_harp ",
                org.bukkit.NamespacedKey.minecraft("block.note_block.harp")));
        assertFalse(FeedbackService.matchesLegacySound("BLOCK_BEACON_POWER_SELECT",
                org.bukkit.NamespacedKey.minecraft("block.beacon.activate")));
        assertFalse(FeedbackService.matchesLegacySound("BLOCK_BEACON_POWER_SELECT",
                org.bukkit.NamespacedKey.fromString("custom:block.beacon.power_select")));
    }

    @Test
    void boundsFeedbackAndRejectsNonFiniteValues() {
        assertEquals(256f, FeedbackService.bounded(1000, 0, 256, 8));
        assertEquals(0f, FeedbackService.bounded(-10, 0, 256, 8));
        assertEquals(8f, FeedbackService.bounded(Double.NaN, 0, 256, 8));
        assertEquals(8f, FeedbackService.bounded(Double.POSITIVE_INFINITY, 0, 256, 8));
    }

    @Test
    void normalizesCoordinatesWithoutReadingBlocksOrChunks() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Location location = new Location(world, -1.1, 64.9, 3.9, 90, 45);
        StoredPoint point = StoredPoint.fromLocation(location, true);
        assertEquals(-2, point.x());
        assertEquals(64, point.y());
        assertEquals(3, point.z());
        assertEquals(0f, point.yaw());
        verify(world).getName();
        verifyNoMoreInteractions(world);
    }

    @Test
    void preservesPreciseCoordinatesWhenNormalizationIsDisabled() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        StoredPoint point = StoredPoint.fromLocation(new Location(world, -1.1, 64.9, 3.9, 90, 45), false);
        assertEquals(-1.1, point.x());
        assertEquals(90, point.yaw());
    }

    @Test
    void cooldownsAreCachedAndPlayerStateCanBeCleared() {
        VanillaPoints plugin = mock(VanillaPoints.class);
        MessageService messages = mock(MessageService.class);
        var config = new YamlConfiguration();
        config.set("cooldowns.default", "60s");
        config.set("rate-limit.max-commands", 0);
        when(plugin.getConfig()).thenReturn(config);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(messages.component(eq(player), eq("cooldown-active"), anyMap()))
                .thenReturn(net.kyori.adventure.text.Component.text("cooldown"));
        var limits = new CommandLimitService(plugin, messages);
        assertTrue(limits.check(player, "home"));
        config.set("cooldowns.default", "0s");
        assertFalse(limits.check(player, "home"), "Configuration changes require a reload");
        limits.clear(player.getUniqueId());
        assertTrue(limits.check(player, "home"));
    }

    @Test
    void rateLimitAndBypassRemainConfigurable() {
        VanillaPoints plugin = mock(VanillaPoints.class);
        MessageService messages = mock(MessageService.class);
        var config = new YamlConfiguration();
        config.set("cooldowns.default", "0s");
        config.set("rate-limit.window", "60s");
        config.set("rate-limit.max-commands", 1);
        config.set("cooldowns.bypass-permission", "custom.bypass");
        when(plugin.getConfig()).thenReturn(config);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(messages.component(eq(player), eq("rate-limit-active"), anyMap()))
                .thenReturn(net.kyori.adventure.text.Component.text("limited"));
        var limits = new CommandLimitService(plugin, messages);
        assertTrue(limits.check(player, "home"));
        assertFalse(limits.check(player, "warp"));
        when(player.hasPermission("custom.bypass")).thenReturn(true);
        assertTrue(limits.check(player, "warp"));
    }
}
