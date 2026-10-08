package com.osiris.upgrader.util;

import org.bukkit.command.CommandSender;

public final class Permissions {
    public static final String USE = "osirisupgrader.use";
    public static final String RELOAD = "osirisupgrader.reload";
    public static final String GIVE = "osirisupgrader.give";
    public static final String SPAWNER = "osirisupgrader.spawner";
    public static final String BREAK = "osirisupgrader.break";
    public static final String CLEAR = "osirisupgrader.clear";
    public static final String BYPASS_COOLDOWN = "osirisupgrader.bypass-cooldown";
    public static final String REWARDS = "osirisupgrader.rewards";
    public static final String BASE = "osirisupgrader.base";
    public static final String PRECISE_CHANCE = "osirisupgrader.precise-chance";
    public static final String HISTORY = "osirisupgrader.history";
    public static final String HISTORY_CLEAR = "osirisupgrader.historyclear";

    private Permissions() {
    }

    public static boolean has(CommandSender sender, String node) {
        return sender.isOp() || sender.hasPermission(node);
    }
}
