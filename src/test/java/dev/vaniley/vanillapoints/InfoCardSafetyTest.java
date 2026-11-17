package dev.vaniley.vanillapoints;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InfoCardSafetyTest {
    private StoredPoint point() {
        return StoredPoint.of("world", 1600, 64, -1600, 0, 0, "", "", "", true, "", 0);
    }

    @Test
    void disabledInfoFieldsDoNotQueryWorldState() throws Exception {
        VanillaPoints plugin = mock(VanillaPoints.class, CALLS_REAL_METHODS);
        var config = new YamlConfiguration();
        for (String field : new String[]{"biome", "time", "weather", "age"}) {
            config.set("info-card.show-" + field, false);
        }
        doReturn(config).when(plugin).getConfig();
        Method method = VanillaPoints.class.getDeclaredMethod("pointPlaceholders", Player.class, StoredPoint.class, Map.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        var placeholders = (Map<String, String>) method.invoke(plugin, mock(Player.class), point(), Map.of());
        assertEquals("", placeholders.get("biome"));
        assertEquals("", placeholders.get("time"));
        verify(plugin, never()).getServer();
    }

    @Test
    void foreignRegionAndUnloadedChunksNeverReadBiome() throws Exception {
        VanillaPoints plugin = mock(VanillaPoints.class, CALLS_REAL_METHODS);
        Server server = mock(Server.class);
        World world = mock(World.class);
        Player player = mock(Player.class);
        MessageService messages = mock(MessageService.class);
        doReturn(server).when(plugin).getServer();
        when(server.getWorld("world")).thenReturn(world);
        when(messages.text(player, "info-card-unknown")).thenReturn("unknown");
        var field = VanillaPoints.class.getDeclaredField("messages");
        field.setAccessible(true);
        field.set(plugin, messages);
        Method method = VanillaPoints.class.getDeclaredMethod("pointBiome", Player.class, StoredPoint.class);
        method.setAccessible(true);
        try (var bukkit = mockStatic(Bukkit.class)) {
            assertEquals("unknown", method.invoke(plugin, player, point()));
            verify(world, never()).isChunkLoaded(anyInt(), anyInt());
            bukkit.when(() -> Bukkit.isOwnedByCurrentRegion(world, 100, -100)).thenReturn(true);
            assertEquals("unknown", method.invoke(plugin, player, point()));
            verify(world).isChunkLoaded(100, -100);
            verify(world, never()).getBiome(anyInt(), anyInt(), anyInt());
        }
    }
}
