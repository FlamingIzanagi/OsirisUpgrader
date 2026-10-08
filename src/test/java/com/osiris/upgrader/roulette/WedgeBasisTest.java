package com.osiris.upgrader.roulette;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WedgeBasisTest {
    @Test
    void wedgesStayInTheDiscForEveryFacing() {
        for (float yaw : new float[] {0f, 90f, 180f, 270f}) {
            double radians = Math.toRadians(yaw);
            float towardX = (float) Math.sin(radians);
            float towardZ = (float) -Math.cos(radians);
            for (int slice = 0; slice < 16; slice++) {
                float[] basis = WheelMath.wedgeBasis(yaw, slice * Math.PI * 2.0d / 16.0d);
                assertEquals(0.0d, dot(basis[0], basis[1], basis[2], towardX, 0f, towardZ), 0.0001d);
                assertEquals(0.0d, dot(basis[3], basis[4], basis[5], towardX, 0f, towardZ), 0.0001d);
                assertEquals(0.0d, dot(basis[0], basis[1], basis[2], basis[3], basis[4], basis[5]), 0.0001d);
            }
        }
    }

    private static double dot(float ax, float ay, float az, float bx, float by, float bz) {
        return ax * bx + ay * by + az * bz;
    }
}
