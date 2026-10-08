package com.osiris.upgrader.roulette;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChanceCalculatorTest {
    @Test
    void directRatioUsesBetOverTarget() {
        assertEquals(44.44d, ChanceCalculator.calculate(24, 54, 1.0d, 1.0d, 95.0d), 0.01d);
    }

    @Test
    void modFactorMatchesReferenceScreenshot() {
        assertEquals(40.0d, ChanceCalculator.calculate(24, 54, 0.9d, 1.0d, 95.0d), 0.01d);
    }

    @Test
    void clampsToConfiguredCeiling() {
        assertEquals(95.0d, ChanceCalculator.calculate(1000, 10, 1.0d, 1.0d, 95.0d), 0.001d);
    }

    @Test
    void clampsToConfiguredFloor() {
        assertEquals(1.0d, ChanceCalculator.calculate(1, 100_000, 1.0d, 1.0d, 95.0d), 0.001d);
    }

    @Test
    void invalidValuesDoNotSpin() {
        assertEquals(0.0d, ChanceCalculator.calculate(0, 10, 1.0d, 1.0d, 95.0d), 0.001d);
        assertEquals(0.0d, ChanceCalculator.calculate(10, 0, 1.0d, 1.0d, 95.0d), 0.001d);
    }
}
