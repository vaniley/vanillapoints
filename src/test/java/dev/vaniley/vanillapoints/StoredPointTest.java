package dev.vaniley.vanillapoints;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoredPointTest {
    private StoredPoint sample(double x, double y, double z) {
        return StoredPoint.of("world", x, y, z, 0.0F, 0.0F, "desc", "TORCH", "bases", false, "Steve", 123L);
    }

    @Test
    void blockCoordinatesFloorTowardsNegativeInfinity() {
        StoredPoint point = sample(3.9D, 64.0D, -1.1D);
        assertEquals(3, point.blockX());
        assertEquals(64, point.blockY());
        assertEquals(-2, point.blockZ());
    }

    @Test
    void retainsMetadata() {
        StoredPoint point = sample(0, 0, 0);
        assertEquals("desc", point.description());
        assertEquals("TORCH", point.icon());
        assertEquals("bases", point.category());
        assertFalse(point.publicVisible());
        assertEquals("Steve", point.createdBy());
        assertEquals(123L, point.createdAt());
    }

    @Test
    void withMetadataKeepsLocationButReplacesMetadata() {
        StoredPoint updated = sample(1, 2, 3).withMetadata("new", "STONE", "shops", true, "Alex", 999L);
        assertEquals(1, updated.blockX());
        assertEquals("new", updated.description());
        assertEquals("shops", updated.category());
        assertTrue(updated.publicVisible());
        assertEquals("Alex", updated.createdBy());
    }

    @Test
    void rejectsBlankWorld() {
        assertThrows(IllegalArgumentException.class, () -> StoredPoint.of("", 0, 0, 0, 0, 0, "", "", "", true, "", 0L));
    }
}
