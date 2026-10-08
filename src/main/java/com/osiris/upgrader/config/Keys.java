package com.osiris.upgrader.config;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public final class Keys {
    public static NamespacedKey ITEM_TYPE;
    public static NamespacedKey ROULETTE_ID;
    public static NamespacedKey ROLE;
    public static NamespacedKey SLICE;

    private Keys() {
    }

    public static void init(Plugin plugin) {
        ITEM_TYPE = new NamespacedKey(plugin, "item_type");
        ROULETTE_ID = new NamespacedKey(plugin, "roulette_id");
        ROLE = new NamespacedKey(plugin, "role");
        SLICE = new NamespacedKey(plugin, "slice");
    }
}
