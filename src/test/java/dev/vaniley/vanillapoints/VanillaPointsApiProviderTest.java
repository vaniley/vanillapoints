package dev.vaniley.vanillapoints;

import dev.vaniley.vanillapoints.api.PointInfo;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VanillaPointsApiProviderTest {
    private final VanillaPoints plugin = mock(VanillaPoints.class);
    private final AtomicReference<PointService> active = new AtomicReference<>();
    private final AtomicInteger stateLockDepth = new AtomicInteger();

    private VanillaPointsApiProvider provider(PointService initial) {
        active.set(initial);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.withState(any())).thenAnswer(invocation -> {
            stateLockDepth.incrementAndGet();
            try {
                return ((Supplier<?>) invocation.getArgument(0)).get();
            } finally {
                stateLockDepth.decrementAndGet();
            }
        });
        when(plugin.currentPoints()).thenAnswer(invocation -> {
            assertTrue(stateLockDepth.get() > 0, "service lookup must be inside lifecycle lock");
            return active.get();
        });
        return new VanillaPointsApiProvider(plugin, initial);
    }

    private StoredPoint point(double x) {
        return StoredPoint.of("world", x, 64, 0, 0, 0, "", "", "", true, "", 0);
    }

    @Test
    void retainedProviderReadsAndMutatesCurrentServiceAfterReload() {
        PointService old = mock(PointService.class);
        PointService replacement = mock(PointService.class);
        VanillaPointsApiProvider api = provider(old);
        when(old.spawn()).thenReturn(Optional.of(point(1)));
        when(replacement.spawn()).thenAnswer(invocation -> {
            assertTrue(stateLockDepth.get() > 0);
            return Optional.of(point(2));
        });
        when(replacement.deleteWarp(null, "test")).thenAnswer(invocation -> {
            assertTrue(stateLockDepth.get() > 0);
            return PointMutationResult.SUCCESS;
        });

        assertEquals(1, api.getSpawnInfo().orElseThrow().x());
        active.set(replacement);
        assertEquals(2, api.getSpawnInfo().orElseThrow().x());
        assertTrue(api.deleteWarp("test"));
        verify(old, never()).deleteWarp(any(), any());
        verify(replacement).deleteWarp(null, "test");
    }

    @Test
    void disabledProviderDoesNotUseDetachedStorage() {
        PointService old = mock(PointService.class);
        VanillaPointsApiProvider api = provider(old);
        active.set(null);
        UUID owner = UUID.randomUUID();

        assertTrue(api.getSpawnInfo().isEmpty());
        assertTrue(api.getSpawnInfo("world").isEmpty());
        assertTrue(api.getHomeInfo(owner).isEmpty());
        assertTrue(api.getHomeInfo(owner, "home").isEmpty());
        assertTrue(api.getWarpInfo("warp").isEmpty());
        assertTrue(api.listHomes(owner).isEmpty());
        assertTrue(api.listWarps().isEmpty());
        assertFalse(api.setSpawn(mock(Location.class)));
        assertFalse(api.deleteHome(owner, "home"));
        assertFalse(api.deleteWarp("warp"));
        assertFalse(api.deleteSpawn(null));
        verifyNoInteractions(old);
    }

    @Test
    void disabledPluginCannotMutateEvenIfServiceRetainedAfterFailedShutdown() {
        PointService old = mock(PointService.class);
        VanillaPointsApiProvider api = provider(old);
        when(plugin.isEnabled()).thenReturn(false);

        assertTrue(api.getSpawnInfo().isEmpty());
        assertTrue(api.listWarps().isEmpty());
        assertFalse(api.deleteWarp("warp"));
        verifyNoInteractions(old);
    }

    @Test
    void nameListsAreImmutableDetachedSnapshots() {
        PointService points = mock(PointService.class);
        VanillaPointsApiProvider api = provider(points);
        UUID owner = UUID.randomUUID();
        Map<String, StoredPoint> homes = new HashMap<>();
        homes.put("home", point(1));
        Set<String> warps = new HashSet<>(Set.of("warp"));
        when(points.homes(owner)).thenReturn(homes);
        when(points.warpNames()).thenReturn(warps);

        Collection<String> homeSnapshot = api.listHomes(owner);
        Collection<String> warpSnapshot = api.listWarps();
        homes.clear();
        warps.clear();
        assertEquals(Set.of("home"), Set.copyOf(homeSnapshot));
        assertEquals(Set.of("warp"), Set.copyOf(warpSnapshot));
        assertThrows(UnsupportedOperationException.class, homeSnapshot::clear);
        assertThrows(UnsupportedOperationException.class, warpSnapshot::clear);
    }

    @Test
    void allLocationReadsRejectAsyncWorkersEvenWhenPointDoesNotExist() {
        PointService points = mock(PointService.class);
        VanillaPointsApiProvider api = provider(points);
        UUID owner = UUID.randomUUID();
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            assertThrows(IllegalStateException.class, api::getSpawn);
            assertThrows(IllegalStateException.class, () -> api.getSpawn("world"));
            assertThrows(IllegalStateException.class, () -> api.getHome(owner));
            assertThrows(IllegalStateException.class, () -> api.getHome(owner, "home"));
            assertThrows(IllegalStateException.class, () -> api.getWarp("warp"));
            PointInfo info = PointInfoMapper.toInfo(point(1));
            assertThrows(IllegalStateException.class, info::toLocation);
        }
        verifyNoInteractions(points);
    }

    @Test
    void pointInfoAndNameReadsNeedNoBukkitContext() {
        PointService points = mock(PointService.class);
        VanillaPointsApiProvider api = provider(points);
        when(points.spawn()).thenReturn(Optional.of(point(1)));
        when(points.warpNames()).thenReturn(Set.of("warp"));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            assertEquals(1, api.getSpawnInfo().orElseThrow().x());
            assertEquals(Set.of("warp"), Set.copyOf(api.listWarps()));
            bukkit.verifyNoInteractions();
        }
    }

    @Test
    void pointInfoConversionOnTickThreadResolvesOnlyLoadedWorld() {
        PointInfo info = PointInfoMapper.toInfo(point(1));
        World world = mock(World.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            Location location = info.toLocation().orElseThrow();
            assertSame(world, location.getWorld());
            assertEquals(1, location.getX());
            verifyNoInteractions(world);
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(null);
            assertTrue(info.toLocation().isEmpty());
        }
    }
}
