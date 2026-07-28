package dev.vaniley.vanillapoints;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerTest {
    @Test
    void detectsNewerVersions() {
        assertTrue(UpdateChecker.isNewer("1.3.0", "1.2.5"));
        assertTrue(UpdateChecker.isNewer("2.0.0", "1.9.9"));
        assertTrue(UpdateChecker.isNewer("1.3.1", "1.3.0"));
    }

    @Test
    void treatsEqualOrOlderAsNotNewer() {
        assertFalse(UpdateChecker.isNewer("1.2.5", "1.2.5"));
        assertFalse(UpdateChecker.isNewer("1.2.4", "1.2.5"));
        assertFalse(UpdateChecker.isNewer("1.2", "1.2.0"));
    }

    @Test
    void toleratesSuffixesAndShortVersions() {
        assertTrue(UpdateChecker.isNewer("1.3.0-SNAPSHOT", "1.2.5"));
        assertFalse(UpdateChecker.isNewer("1.2", "1.3"));
    }
}
