package com.osiris.upgrader.roulette;

import com.osiris.upgrader.OsirisUpgraderPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class SpinCooldowns {
    private final OsirisUpgraderPlugin plugin;
    private final File file;
    private final Map<UUID, Long> lastSpin = new LinkedHashMap<>();

    public SpinCooldowns(OsirisUpgraderPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "cooldowns.yml");
    }

    public void load() {
        lastSpin.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            try {
                lastSpin.put(UUID.fromString(key), players.getLong(key));
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "Cooldown inválido: " + key, ex);
            }
        }
    }

    public long remainingMillis(UUID playerId) {
        if (playerId == null || plugin.configs().spinCooldownSeconds() <= 0L) {
            return 0L;
        }
        Long last = lastSpin.get(playerId);
        if (last == null) {
            return 0L;
        }
        long duration = plugin.configs().spinCooldownSeconds() * 1_000L;
        return Math.max(0L, last + duration - System.currentTimeMillis());
    }

    public void mark(UUID playerId) {
        if (playerId == null || plugin.configs().spinCooldownSeconds() <= 0L) {
            return;
        }
        lastSpin.put(playerId, System.currentTimeMillis());
        save();
    }

    public static String formatRemaining(long millis) {
        long total = Math.max(1L, (millis + 999L) / 1000L);
        long hours = total / 3600L;
        long minutes = (total % 3600L) / 60L;
        long seconds = total % 60L;
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (hours > 0L) {
            parts.add(hours + (hours == 1L ? " hora" : " horas"));
        }
        if (minutes > 0L) {
            parts.add(minutes + (minutes == 1L ? " minuto" : " minutos"));
        }
        if (seconds > 0L || parts.isEmpty()) {
            parts.add(seconds + (seconds == 1L ? " segundo" : " segundos"));
        }
        if (parts.size() == 1) {
            return parts.getFirst();
        }
        if (parts.size() == 2) {
            return parts.get(0) + " y " + parts.get(1);
        }
        return parts.get(0) + ", " + parts.get(1) + " y " + parts.get(2);
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection players = yaml.createSection("players");
        long keepAfter = System.currentTimeMillis() - Math.round(8760.0d * 3_600_000.0d);
        for (Map.Entry<UUID, Long> entry : lastSpin.entrySet()) {
            if (entry.getValue() < keepAfter) {
                continue;
            }
            players.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo guardar cooldowns.yml", ex);
        }
    }
}
