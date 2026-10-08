package com.osiris.upgrader.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class Sounds {
    private final com.osiris.upgrader.config.ConfigManager configs;

    public Sounds(com.osiris.upgrader.config.ConfigManager configs) {
        this.configs = configs;
    }

    public void play(Player player, String key) {
        play(player, key, 1.0f);
    }

    public void play(Player player, String key, float pitch) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Sound sound = resolve(configs.sound(key));
        if (sound == null) {
            return;
        }
        player.playSound(player.getLocation(), sound, SoundCategory.MASTER, 1.0f, pitch);
    }

    public static Sound resolve(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String raw = input.trim().toLowerCase(Locale.ROOT);
        org.bukkit.NamespacedKey key = null;
        if (raw.contains(":")) {
            key = org.bukkit.NamespacedKey.fromString(raw);
        } else if (raw.contains(".")) {
            key = org.bukkit.NamespacedKey.minecraft(raw);
        }
        if (key != null) {
            Sound sound = Registry.SOUNDS.get(key);
            if (sound != null) {
                return sound;
            }
        }
        String enumStyle = input.trim().toUpperCase(Locale.ROOT).replace('.', '_').replace(':', '_');
        if (enumStyle.startsWith("MINECRAFT_")) {
            enumStyle = enumStyle.substring("MINECRAFT_".length());
        }
        for (Sound sound : Registry.SOUNDS) {
            NamespacedKey soundKey = Registry.SOUNDS.getKey(sound);
            if (soundKey == null) {
                continue;
            }
            String style = soundKey.getKey().toUpperCase(Locale.ROOT).replace('.', '_');
            if (style.equals(enumStyle) || soundKey.getKey().equalsIgnoreCase(raw)) {
                return sound;
            }
        }
        return null;
    }
}
