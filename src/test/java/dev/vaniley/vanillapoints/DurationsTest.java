package dev.vaniley.vanillapoints;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurationsTest {
    @Test
    void parsesUnitSuffixes() {
        assertEquals(2_000L, Durations.parse("2s").getAsLong());
        assertEquals(500L, Durations.parse("500ms").getAsLong());
        assertEquals(60_000L, Durations.parse("1m").getAsLong());
        assertEquals(3_600_000L, Durations.parse("1h").getAsLong());
    }

    @Test
    void parsesBareNumberAsSeconds() {
        assertEquals(3_000L, Durations.parse("3").getAsLong());
        assertEquals(5_000L, Durations.parse(5).getAsLong());
    }

    @Test
    void roundsFractionalValuesUp() {
        assertEquals(1_500L, Durations.parse("1.5s").getAsLong());
    }

    @Test
    void rejectsGarbageAndNull() {
        assertTrue(Durations.parse("abc").isEmpty());
        assertTrue(Durations.parse("").isEmpty());
        assertEquals(OptionalLong.empty(), Durations.parse(null));
    }

    @Test
    void formatsCompactly() {
        assertEquals("1s", Durations.format(0L));
        assertEquals("1s", Durations.format(999L));
        assertEquals("45s", Durations.format(45_000L));
        assertEquals("2m", Durations.format(120_000L));
        assertEquals("2m 5s", Durations.format(125_000L));
    }

    @Test
    void formatRoundsPartialSecondsUp() {
        assertEquals("2s", Durations.format(1_500L));
    }
}
