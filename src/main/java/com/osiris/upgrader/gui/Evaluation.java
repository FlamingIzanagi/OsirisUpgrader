package com.osiris.upgrader.gui;

public record Evaluation(
        Status status,
        double chance,
        double unitBet,
        double unitTarget,
        int betAmount,
        int targetAmount
) {
    public enum Status {
        OK,
        NO_ORIGIN,
        NO_TARGET,
        NO_VALUE
    }

    public static Evaluation missing(Status status) {
        return new Evaluation(status, 0.0d, 0.0d, 0.0d, 0, 0);
    }

    public boolean ok() {
        return status == Status.OK && chance > 0.0d;
    }

    public double betTotal() {
        return unitBet * betAmount;
    }

    public double targetTotal() {
        return unitTarget * targetAmount;
    }
}
