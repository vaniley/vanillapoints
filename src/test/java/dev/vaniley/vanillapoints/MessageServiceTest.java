package dev.vaniley.vanillapoints;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessageServiceTest {
    @TempDir
    Path directory;

    @Test
    void loadsPlayerLanguagesBeforeConcurrentMessageRequests() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        var config = new YamlConfiguration();
        config.set("settings.language", "en");
        config.set("settings.per-player-permissions", true);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getResource(anyString())).thenAnswer(call ->
                getClass().getClassLoader().getResourceAsStream(call.getArgument(0)));
        doAnswer(call -> {
            String name = call.getArgument(0);
            try (var source = getClass().getClassLoader().getResourceAsStream(name)) {
                assertNotNull(source);
                Files.copy(source, directory.resolve(name));
            }
            return null;
        }).when(plugin).saveResource(anyString(), eq(false));

        MessageService messages = new MessageService(plugin);
        messages.load();
        for (String language : MessageService.SUPPORTED_LANGUAGES) {
            assertTrue(Files.exists(directory.resolve(language.equals("en") ? "messages.yml" : "messages_" + language + ".yml")));
        }
        Player player = mock(Player.class);
        when(player.hasPermission("vanillapoints.lang.ru")).thenReturn(true);
        String expected = messages.text(player, "reload-complete");
        clearInvocations(plugin);
        var calls = new ArrayList<Callable<String>>();
        for (int i = 0; i < 128; i++) {
            calls.add(() -> messages.text(player, "reload-complete"));
        }
        try (var workers = Executors.newFixedThreadPool(8)) {
            for (var result : workers.invokeAll(calls)) {
                assertEquals(expected, result.get());
            }
        }
        verifyNoInteractions(plugin);
    }
}
