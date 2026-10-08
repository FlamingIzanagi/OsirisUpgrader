package com.osiris.upgrader.roulette;

import java.util.Random;

public final class WheelMath {
    private static final double TWO_PI = Math.PI * 2.0d;

    private WheelMath() {
    }

    public static float[] wedgeBasis(float yawDegrees, double angle) {
        double yaw = Math.toRadians(yawDegrees);
        float rightX = (float) -Math.cos(yaw);
        float rightZ = (float) -Math.sin(yaw);
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        return new float[] {
                rightX * cos, -sin, rightZ * cos,
                rightX * sin, cos, rightZ * sin
        };
    }

    public static double normalize(double angle) {
        angle %= TWO_PI;
        if (angle > Math.PI) {
            angle -= TWO_PI;
        } else if (angle <= -Math.PI) {
            angle += TWO_PI;
        }
        return angle;
    }

    public static double positive(double angle) {
        angle %= TWO_PI;
        if (angle < 0.0d) {
            angle += TWO_PI;
        }
        return angle;
    }

    public static boolean inSuccessZone(double angle, double chancePercent) {
        if (chancePercent <= 0.0d) {
            return false;
        }
        if (chancePercent >= 100.0d) {
            return true;
        }
        double halfSpan = (chancePercent / 100.0d) * Math.PI;
        return Math.abs(normalize(angle)) <= halfSpan + 1.0e-9d;
    }

    public static double landingAngle(boolean win, double chancePercent, Random random) {
        double clamped = Math.max(0.0d, Math.min(100.0d, chancePercent));
        double span = (clamped / 100.0d) * TWO_PI;
        if (win) {
            double half = span / 2.0d;
            double margin = Math.min(half * 0.15d, 0.08d);
            if (half <= margin + 1.0e-6d) {
                return 0.0d;
            }
            return random.nextDouble(-half + margin, half - margin);
        }
        double start = span / 2.0d;
        double end = TWO_PI - span / 2.0d;
        if (end - start < 0.05d) {
            return Math.PI;
        }
        double margin = Math.min(0.08d, (end - start) * 0.1d);
        return random.nextDouble(start + margin, end - margin);
    }
}
