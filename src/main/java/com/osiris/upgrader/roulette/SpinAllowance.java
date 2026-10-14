package com.osiris.upgrader.roulette;

/**
 * Giros diarios dentro de una ventana y giros extra que no se reinician con ella.
 * Los diarios se gastan primero. Los extra solo se gastan cuando los diarios se acabaron.
 */
public final class SpinAllowance {
    public record State(long windowStartMillis, int used, int extra) {
        public State {
            if (windowStartMillis < 0L) {
                windowStartMillis = 0L;
            }
            if (used < 0) {
                used = 0;
            }
            if (extra < 0) {
                extra = 0;
            }
        }

        public static State empty() {
            return new State(0L, 0, 0);
        }
    }

    private SpinAllowance() {
    }

    public static State refreshed(State state, long cooldownMillis, long now) {
        if (state == null) {
            return State.empty();
        }
        if (state.windowStartMillis <= 0L || cooldownMillis <= 0L || now >= state.windowStartMillis + cooldownMillis) {
            return new State(0L, 0, state.extra);
        }
        return state;
    }

    public static int dailyRemaining(State state, int dailyMax, long cooldownMillis, long now) {
        if (cooldownMillis <= 0L) {
            return Integer.MAX_VALUE;
        }
        if (dailyMax <= 0) {
            return 0;
        }
        State current = refreshed(state, cooldownMillis, now);
        return Math.max(0, dailyMax - current.used);
    }

    public static boolean available(State state, int dailyMax, long cooldownMillis, long now) {
        if (cooldownMillis <= 0L) {
            return true;
        }
        State current = refreshed(state, cooldownMillis, now);
        return dailyRemaining(current, dailyMax, cooldownMillis, now) > 0 || current.extra > 0;
    }

    public static long remainingMillis(State state, int dailyMax, long cooldownMillis, long now) {
        if (available(state, dailyMax, cooldownMillis, now) || cooldownMillis <= 0L) {
            return 0L;
        }
        State current = refreshed(state, cooldownMillis, now);
        if (current.windowStartMillis <= 0L) {
            return 0L;
        }
        return Math.max(0L, current.windowStartMillis + cooldownMillis - now);
    }

    public static State consume(State state, int dailyMax, long cooldownMillis, long now) {
        if (cooldownMillis <= 0L) {
            return refreshed(state, cooldownMillis, now);
        }
        State current = refreshed(state, cooldownMillis, now);
        if (dailyMax > 0 && current.used < dailyMax) {
            long window = current.used == 0 ? now : current.windowStartMillis;
            return new State(window, current.used + 1, current.extra);
        }
        if (current.extra > 0) {
            return new State(current.windowStartMillis, current.used, current.extra - 1);
        }
        return current;
    }

    public static State grant(State state, int amount) {
        if (state == null) {
            state = State.empty();
        }
        if (amount <= 0) {
            return state;
        }
        long sum = (long) state.extra + amount;
        int extra = sum > 1_000_000L ? 1_000_000 : (int) sum;
        return new State(state.windowStartMillis, state.used, extra);
    }
}
