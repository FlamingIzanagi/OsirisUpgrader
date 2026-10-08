package com.osiris.upgrader.roulette;

/**
 * Probabilidad = (valor apostado / valor destino) * 100 * factor.
 * El factor 1.0 es la proporción directa. El mod Upgrader usa 0.9.
 */
public final class ChanceCalculator {
    private ChanceCalculator() {
    }

    public static double calculate(double betValue, double targetValue, double factor, double min, double max) {
        if (betValue <= 0.0d || targetValue <= 0.0d || factor <= 0.0d) {
            return 0.0d;
        }
        double lower = Math.min(min, max);
        double upper = Math.max(min, max);
        double raw = (betValue / targetValue) * 100.0d * factor;
        return Math.max(lower, Math.min(upper, raw));
    }
}
