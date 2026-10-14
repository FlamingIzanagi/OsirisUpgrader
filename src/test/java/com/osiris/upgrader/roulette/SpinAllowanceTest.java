package com.osiris.upgrader.roulette;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpinAllowanceTest {
    private static final long DAY = 86_400_000L;

    @Test
    void oneDailySpinLocksUntilTheWindowEnds() {
        SpinAllowance.State state = SpinAllowance.consume(SpinAllowance.State.empty(), 1, DAY, 1_000L);
        assertEquals(1, state.used());
        assertFalse(SpinAllowance.available(state, 1, DAY, 1_000L + 10L));
        assertEquals(DAY - 10L, SpinAllowance.remainingMillis(state, 1, DAY, 1_000L + 10L));
        assertTrue(SpinAllowance.available(state, 1, DAY, 1_000L + DAY));
        assertEquals(1, SpinAllowance.dailyRemaining(state, 1, DAY, 1_000L + DAY));
    }

    @Test
    void severalDailySpinsShareOneWindow() {
        SpinAllowance.State state = SpinAllowance.State.empty();
        state = SpinAllowance.consume(state, 3, DAY, 5_000L);
        state = SpinAllowance.consume(state, 3, DAY, 6_000L);
        assertEquals(1, SpinAllowance.dailyRemaining(state, 3, DAY, 7_000L));
        assertEquals(5_000L, state.windowStartMillis());
        state = SpinAllowance.consume(state, 3, DAY, 8_000L);
        assertFalse(SpinAllowance.available(state, 3, DAY, 9_000L));
        assertEquals(0, state.extra());
    }

    @Test
    void extraSpinsAreUsedOnlyAfterDailySpins() {
        SpinAllowance.State state = SpinAllowance.grant(SpinAllowance.State.empty(), 2);
        state = SpinAllowance.consume(state, 1, DAY, 100L);
        assertEquals(2, state.extra());
        assertEquals(1, state.used());
        state = SpinAllowance.consume(state, 1, DAY, 200L);
        assertEquals(1, state.extra());
        assertTrue(SpinAllowance.available(state, 1, DAY, 300L));
    }

    @Test
    void extraSpinsSurviveTheDailyReset() {
        SpinAllowance.State state = new SpinAllowance.State(1_000L, 1, 4);
        SpinAllowance.State reset = SpinAllowance.refreshed(state, DAY, 1_000L + DAY);
        assertEquals(0, reset.used());
        assertEquals(4, reset.extra());
        assertEquals(1, SpinAllowance.dailyRemaining(reset, 1, DAY, 1_000L + DAY));
    }

    @Test
    void zeroDailySpinsLeaveOnlyExtras() {
        SpinAllowance.State empty = SpinAllowance.State.empty();
        assertFalse(SpinAllowance.available(empty, 0, DAY, 50L));
        SpinAllowance.State granted = SpinAllowance.grant(empty, 1);
        assertTrue(SpinAllowance.available(granted, 0, DAY, 50L));
        SpinAllowance.State spent = SpinAllowance.consume(granted, 0, DAY, 50L);
        assertEquals(0, spent.extra());
        assertEquals(0, spent.used());
    }

    @Test
    void disabledCooldownDoesNotSpendExtras() {
        SpinAllowance.State granted = SpinAllowance.grant(SpinAllowance.State.empty(), 3);
        assertTrue(SpinAllowance.available(granted, 1, 0L, 10L));
        assertEquals(3, SpinAllowance.consume(granted, 1, 0L, 10L).extra());
    }
}
