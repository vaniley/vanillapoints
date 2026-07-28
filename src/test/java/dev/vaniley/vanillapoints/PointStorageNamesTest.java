package dev.vaniley.vanillapoints;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PointStorageNamesTest {
    @Test
    void acceptsSimpleNames() {
        assertTrue(PointStorage.isValidHomeName("base"));
        assertTrue(PointStorage.isValidHomeName("My-Home_2"));
        assertTrue(PointStorage.isValidWarpName("spawn-1"));
    }

    @Test
    void rejectsInvalidNames() {
        assertFalse(PointStorage.isValidHomeName(null));
        assertFalse(PointStorage.isValidHomeName(""));
        assertFalse(PointStorage.isValidHomeName("has space"));
        assertFalse(PointStorage.isValidHomeName("дом"));
        assertFalse(PointStorage.isValidWarpName("a".repeat(33)));
    }

    @Test
    void normalizeHomeNameLowercasesAndDefaults() {
        assertEquals("base", PointStorage.normalizeHomeName("BASE"));
        assertEquals(PointStorage.DEFAULT_HOME_NAME, PointStorage.normalizeHomeName(""));
        assertEquals(PointStorage.DEFAULT_HOME_NAME, PointStorage.normalizeHomeName(null));
    }

    @Test
    void normalizeWarpNameLowercases() {
        assertEquals("home1", PointStorage.normalizeWarpName("Home1"));
    }
}
