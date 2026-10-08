package com.osiris.upgrader.roulette;

import com.osiris.upgrader.OsirisUpgraderPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

public final class SpinHistory {
    public static final int PREVIEW = 10;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OsirisUpgraderPlugin plugin;

    public SpinHistory(OsirisUpgraderPlugin plugin) {
        this.plugin = plugin;
    }

    public void record(String player, String betName, int betAmount, int multiplier, double chance, boolean win, String prizeName, int prizeAmount) {
        int factor = Math.max(1, multiplier);
        String prize = win ? clean(prizeName) + " x" + Math.max(1, prizeAmount) : "ninguno";
        String line = "[" + LocalDateTime.now().format(TIME) + "] "
                + clean(player)
                + " | Apuesta: " + clean(betName) + " x" + Math.max(1, betAmount)
                + " | Multiplicador: x" + factor
                + " | Probabilidad: " + String.format(Locale.US, "%.2f", chance) + "%"
                + " | Resultado: " + (win ? "Ganó" : "Perdió")
                + " | Premio: " + prize
                + System.lineSeparator();
        synchronized (this) {
            try {
                Files.writeString(file().toPath(), line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ex) {
                plugin.getLogger().log(Level.WARNING, "No se pudo escribir history.log", ex);
            }
        }
    }

    public List<String> latest() {
        List<String> lines = read();
        if (lines.size() <= PREVIEW) {
            return lines;
        }
        return new ArrayList<>(lines.subList(lines.size() - PREVIEW, lines.size()));
    }

    public int size() {
        return read().size();
    }

    public int clear() {
        synchronized (this) {
            int count = read().size();
            try {
                Files.deleteIfExists(file().toPath());
            } catch (IOException ex) {
                plugin.getLogger().log(Level.WARNING, "No se pudo borrar history.log", ex);
                return 0;
            }
            return count;
        }
    }

    private List<String> read() {
        java.io.File log = file();
        if (!log.isFile()) {
            return List.of();
        }
        try {
            List<String> lines = new ArrayList<>();
            for (String line : Files.readAllLines(log.toPath(), StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
            return lines;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "No se pudo leer history.log", ex);
            return List.of();
        }
    }

    private java.io.File file() {
        return new java.io.File(plugin.getDataFolder(), "history.log");
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) {
            return "desconocido";
        }
        return value.replace('\n', ' ').replace('\r', ' ').replace('|', '/').trim();
    }
}
