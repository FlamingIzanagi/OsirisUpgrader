package com.osiris.upgrader.roulette;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WheelMathTest {
    @Test
    void successZoneIsCenteredOnTheTop() {
        assertTrue(WheelMath.inSuccessZone(0.0d, 40.0d));
        assertFalse(WheelMath.inSuccessZone(Math.PI, 40.0d));
    }

    @Test
    void winLandingStaysInsideTheGreenArc() {
        Random random = new Random(7);
        for (int i = 0; i < 200; i++) {
            double angle = WheelMath.landingAngle(true, 40.0d, random);
            assertTrue(WheelMath.inSuccessZone(angle, 40.0d), "ángulo ganador fuera de zona: " + angle);
        }
    }

    @Test
    void loseLandingStaysOutsideTheGreenArc() {
        Random random = new Random(11);
        for (int i = 0; i < 200; i++) {
            double angle = WheelMath.landingAngle(false, 40.0d, random);
            assertFalse(WheelMath.inSuccessZone(angle, 40.0d), "ángulo perdedor dentro de zona: " + angle);
        }
    }

    @Test
    void narrowAndWideChancesStillSeparateZones() {
        Random random = new Random(3);
        for (double chance : new double[]{1.0d, 50.0d, 95.0d}) {
            for (int i = 0; i < 40; i++) {
                assertTrue(WheelMath.inSuccessZone(WheelMath.landingAngle(true, chance, random), chance));
                assertFalse(WheelMath.inSuccessZone(WheelMath.landingAngle(false, chance, random), chance));
            }
        }
    }
}
