package dev.vaniley.vanillapoints;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EssentialsImporterTest {
    private static final UUID OWNER = UUID.fromString("f56f608c-2d57-4e93-b9dd-9c35a387d360");

    @TempDir
    Path directory;

    private JavaPlugin plugin() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.resolve("VanillaPoints").toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("EssentialsImporterTest"));
        return plugin;
    }

    private void writeEssentialsData() throws Exception {
        Path userdata = Files.createDirectories(directory.resolve("Essentials/userdata"));
        Path warps = Files.createDirectories(directory.resolve("Essentials/warps"));
        Files.writeString(userdata.resolve(OWNER + ".yml"), """
                homes:
                  Home One:
                    world-name: overworld
                    x: 1.5
                    y: 64
                    z: -2.5
                    yaw: 90
                    pitch: 30
                  '!!!':
                    world: world
                    x: 2
                    y: 65
                    z: 3
                  missing-world:
                    x: 5
                """);
        Files.writeString(userdata.resolve("not-a-uuid.yml"), "homes: {}\n");
        Files.writeString(warps.resolve("Market Square.yml"), """
                world: legacy-world
                x: 10
                y: 70
                z: 20
                """);
    }

    @Test
    void readDataReturnsDetachedImmutableSnapshotAndCountsWithoutServerAccess() throws Exception {
        writeEssentialsData();
        JavaPlugin plugin = plugin();
        EssentialsImporter.ImportedData imported;
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            imported = EssentialsImporter.readData(plugin);
            bukkit.verifyNoInteractions();
        }

        assertEquals(new EssentialsImporter.Result(1, 1), imported.result());
        PointStorageSnapshot snapshot = imported.snapshot();
        assertTrue(snapshot.spawns().isEmpty());
        assertEquals(Set.of(OWNER), snapshot.homes().keySet());
        assertEquals(Set.of("homeone"), snapshot.homes().get(OWNER).keySet());
        StoredPoint home = snapshot.homes().get(OWNER).get("homeone");
        assertEquals("overworld", home.worldName());
        assertEquals(1.5, home.x());
        assertEquals(-2.5, home.z());
        assertEquals(90.0F, home.yaw());
        assertEquals(30.0F, home.pitch());
        assertEquals("Essentials", home.createdBy());
        assertEquals(Set.of("marketsquare"), snapshot.warps().keySet());
        assertEquals("legacy-world", snapshot.warps().get("marketsquare").worldName());
        assertThrows(UnsupportedOperationException.class, snapshot.homes()::clear);
        assertThrows(UnsupportedOperationException.class, snapshot.homes().get(OWNER)::clear);
        assertThrows(UnsupportedOperationException.class, snapshot.warps()::clear);

        Files.delete(directory.resolve("Essentials/warps/Market Square.yml"));
        assertEquals(1, snapshot.warps().size(), "snapshot must not depend on later filesystem changes");
    }

    @Test
    void missingEssentialsFolderReturnsEmptySnapshotAndZeroCounts() {
        EssentialsImporter.ImportedData imported = EssentialsImporter.readData(plugin());
        assertTrue(imported.snapshot().isEmpty());
        assertEquals(new EssentialsImporter.Result(0, 0), imported.result());
    }

    @Test
    void existingImportEntryPointStillWritesToItsSuppliedStorage() throws Exception {
        writeEssentialsData();
        PointStorage live = mock(PointStorage.class);
        EssentialsImporter.Result result = new EssentialsImporter(plugin(), live).importData();
        assertEquals(new EssentialsImporter.Result(1, 1), result);
        verify(live).setHome(eq(OWNER), eq("homeone"), any(StoredPoint.class));
        verify(live).setWarp(eq("marketsquare"), any(StoredPoint.class));
        verifyNoMoreInteractions(live);
    }
}
