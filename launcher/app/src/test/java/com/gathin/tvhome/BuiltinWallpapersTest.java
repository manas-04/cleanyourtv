package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Plain JVM tests for which built-in wallpaper is due: in order, one per period, counted in local time. */
public class BuiltinWallpapersTest {
    private static final long HOUR = 60 * 60_000L;
    /** 2026-10-21 00:00 UTC. */
    private static final long MIDNIGHT_UTC = 1_792_540_800_000L;

    @Test
    public void hourlyInOrderTheSameForTheWholeHour() {
        int first = Wallpapers.due(MIDNIGHT_UTC, 0, 60, 14);
        assertEquals(first, Wallpapers.due(MIDNIGHT_UTC + HOUR - 1, 0, 60, 14));
        assertEquals((first + 1) % 14, Wallpapers.due(MIDNIGHT_UTC + HOUR, 0, 60, 14));
        assertEquals(first, Wallpapers.due(MIDNIGHT_UTC + 14 * HOUR, 0, 60, 14));   // round the set and back
    }

    @Test
    public void aShorterPeriodChangesSooner() {
        int first = Wallpapers.due(MIDNIGHT_UTC, 0, 30, 14);
        assertEquals(first, Wallpapers.due(MIDNIGHT_UTC + HOUR / 2 - 1, 0, 30, 14));
        assertEquals((first + 1) % 14, Wallpapers.due(MIDNIGHT_UTC + HOUR / 2, 0, 30, 14));
    }

    @Test
    public void changesOnTheLocalHourInAHalfHourZone() {
        int offset = (int) (5.5 * HOUR);
        long localMidnight = MIDNIGHT_UTC - offset;    // 00:00 at +5:30
        int at = Wallpapers.due(localMidnight, offset, 60, 14);
        assertEquals(Wallpapers.due(MIDNIGHT_UTC, 0, 60, 14), at);    // the same local time, the same picture
        assertEquals(at, Wallpapers.due(localMidnight + HOUR - 1, offset, 60, 14));
        assertEquals((at + 1) % 14, Wallpapers.due(localMidnight + HOUR, offset, 60, 14));
        // Counted in UTC it would change at half past instead.
        assertEquals(Wallpapers.due(localMidnight + HOUR - 1, 0, 60, 14), Wallpapers.due(localMidnight + HOUR, 0, 60, 14));
    }
}
