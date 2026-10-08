package com.osiris.upgrader.config;

import com.osiris.upgrader.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Messages {
    private FileConfiguration yaml;

    public void load(FileConfiguration yaml) {
        this.yaml = yaml;
    }

    public String raw(String path) {
        if (yaml == null) {
            return path;
        }
        String value = yaml.getString(path);
        return value == null ? path : value;
    }

    public List<String> list(String path) {
        if (yaml == null) {
            return List.of();
        }
        return yaml.getStringList(path);
    }

    public List<String> lines(String path) {
        if (yaml == null) {
            return List.of();
        }
        if (yaml.contains(path, true)) {
            return readLines(yaml, path);
        }
        Configuration defaults = yaml.getDefaults();
        if (defaults != null && defaults.contains(path)) {
            return readLines(defaults, path);
        }
        return List.of();
    }

    private static List<String> readLines(Configuration source, String path) {
        Object value = source.get(path, null);
        if (value instanceof String text) {
            return List.of(text);
        }
        if (value instanceof List<?> list) {
            List<String> lines = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    lines.add(String.valueOf(item));
                }
            }
            return lines;
        }
        return List.of();
    }

    public String format(String path, Map<String, String> placeholders) {
        return Text.apply(Text.prizeQuantity(raw(path)), placeholders);
    }

    public List<String> formatList(String path, Map<String, String> placeholders) {
        return list(path).stream().map(line -> Text.apply(Text.prizeQuantity(line), placeholders)).toList();
    }

    public Component component(String path, Map<String, String> placeholders) {
        return Text.component(format(path, placeholders));
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(Text.component(prefix(placeholders) + format("messages." + key, placeholders)));
    }

    public void sendHelp(CommandSender sender) {
        sender.sendMessage(Text.component(prefix(Map.of()) + format("messages.help-header", Map.of())));
        for (String line : list("messages.help")) {
            sender.sendMessage(Text.component(line));
        }
    }

    private String prefix(Map<String, String> placeholders) {
        return format("messages.prefix", placeholders);
    }
}
