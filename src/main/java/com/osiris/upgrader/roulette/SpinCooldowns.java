package com.osiris.upgrader.roulette;

import com.osiris.upgrader.OsirisUpgraderPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class SpinCooldowns {
    private final OsirisUpgraderPlugin plugin;
    private final File file;
    private final Map<UUID, SpinAllowance.State> players = new LinkedHashMap<>();

    public SpinCooldowns(OsirisUpgraderPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "cooldowns.yml");
    }

    public void load() {
        players.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("players");
        if (root == null) {
            return;
        }
        boolean migrated = false;
        for (String key : root.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(key);
                if (root.isConfigurationSection(key)) {
                    ConfigurationSection section = root.getConfigurationSection(key);
                    players.put(playerId, new SpinAllowance.State(
                            section == null ? 0L : section.getLong("window"),
                            section == null ? 0 : section.getInt("used"),
                            section == null ? 0 : section.getInt("extra")
                    ));
                } else {
                    long last = root.getLong(key);
                    players.put(playerId, new SpinAllowance.State(last, last > 0L ? 1 : 0, 0));
                    migrated = true;
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "Cooldown inválido: " + key, ex);
            }
        }
        refreshAll(System.currentTimeMillis());
        if (migrated) {
            save();
        }
    }

    public boolean available(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        return SpinAllowance.available(state(playerId), dailyMax(), cooldownMillis(), System.currentTimeMillis());
    }

    public int dailyRemaining(UUID playerId) {
        return SpinAllowance.dailyRemaining(state(playerId), dailyMax(), cooldownMillis(), System.currentTimeMillis());
    }

    public int extra(UUID playerId) {
        return current(playerId).extra();
    }

    public long remainingMillis(UUID playerId) {
        if (playerId == null) {
            return 0L;
        }
        return SpinAllowance.remainingMillis(state(playerId), dailyMax(), cooldownMillis(), System.currentTimeMillis());
    }

    public void consume(UUID playerId) {
        if (playerId == null || cooldownMillis() <= 0L) {
            return;
        }
        long now = System.currentTimeMillis();
        SpinAllowance.State next = SpinAllowance.consume(state(playerId), dailyMax(), cooldownMillis(), now);
        store(playerId, next);
        save();
    }

    public int grant(UUID playerId, int amount) {
        int extra = rememberGrant(playerId, amount);
        if (playerId != null && amount > 0) {
            save();
        }
        return extra;
    }

    public Map<UUID, Integer> grantAll(Iterable<UUID> playerIds, int amount) {
        Map<UUID, Integer> totals = new LinkedHashMap<>();
        if (playerIds == null || amount <= 0) {
            return totals;
        }
        for (UUID playerId : playerIds) {
            if (playerId != null) {
                totals.put(playerId, rememberGrant(playerId, amount));
            }
        }
        if (!totals.isEmpty()) {
            save();
        }
        return totals;
    }

    private int rememberGrant(UUID playerId, int amount) {
        if (playerId == null || amount <= 0) {
            return extra(playerId);
        }
        SpinAllowance.State next = SpinAllowance.grant(current(playerId), amount);
        store(playerId, next);
        return next.extra();
    }

    public void deny(Player player) {
        UUID playerId = player.getUniqueId();
        long remaining = remainingMillis(playerId);
        Map<String, String> tokens = Map.of(
                "time", formatRemaining(Math.max(1L, remaining)),
                "daily", Integer.toString(dailyRemaining(playerId)),
                "daily_max", Integer.toString(dailyMax()),
                "extra", Integer.toString(extra(playerId))
        );
        if (remaining > 0L) {
            plugin.messages().send(player, "spins-locked", tokens);
            return;
        }
        plugin.messages().send(player, "spins-empty", tokens);
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

    private SpinAllowance.State state(UUID playerId) {
        long now = System.currentTimeMillis();
        SpinAllowance.State current = current(playerId);
        SpinAllowance.State refreshed = SpinAllowance.refreshed(current, cooldownMillis(), now);
        if (!refreshed.equals(current)) {
            store(playerId, refreshed);
        }
        return refreshed;
    }

    private SpinAllowance.State current(UUID playerId) {
        return players.getOrDefault(playerId, SpinAllowance.State.empty());
    }

    private void store(UUID playerId, SpinAllowance.State state) {
        if (state.used() == 0 && state.extra() == 0 && state.windowStartMillis() == 0L) {
            players.remove(playerId);
            return;
        }
        players.put(playerId, state);
    }

    private void refreshAll(long now) {
        for (UUID playerId : new java.util.ArrayList<>(players.keySet())) {
            store(playerId, SpinAllowance.refreshed(players.get(playerId), cooldownMillis(), now));
        }
    }

    private int dailyMax() {
        return plugin.configs().dailySpins();
    }

    private long cooldownMillis() {
        return plugin.configs().spinCooldownSeconds() * 1_000L;
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("players");
        for (Map.Entry<UUID, SpinAllowance.State> entry : players.entrySet()) {
            SpinAllowance.State state = entry.getValue();
            if (state.used() == 0 && state.extra() == 0) {
                continue;
            }
            ConfigurationSection section = root.createSection(entry.getKey().toString());
            section.set("window", state.windowStartMillis());
            section.set("used", state.used());
            section.set("extra", state.extra());
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo guardar cooldowns.yml", ex);
        }
    }
}
