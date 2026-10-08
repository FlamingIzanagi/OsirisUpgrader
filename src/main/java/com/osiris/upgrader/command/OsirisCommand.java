package com.osiris.upgrader.command;

import com.osiris.upgrader.OsirisUpgraderPlugin;
import com.osiris.upgrader.item.ItemMover;
import com.osiris.upgrader.util.Permissions;
import com.osiris.upgrader.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class OsirisCommand implements CommandExecutor, TabCompleter {
    private final OsirisUpgraderPlugin plugin;

    public OsirisCommand(OsirisUpgraderPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            plugin.messages().sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "giveitem" -> give(sender, args, true);
            case "spawner" -> give(sender, args, false);
            case "clear" -> clearLeftovers(sender, label, false);
            case "clearnear" -> clearLeftovers(sender, label, true);
            case "rewards" -> rewards(sender, label);
            case "base" -> base(sender, label);
            case "history" -> history(sender, label);
            case "historyclear" -> historyClear(sender, label);
            default -> plugin.messages().send(sender, "unknown-command");
        }
        return true;
    }

    private void reload(CommandSender sender) {
        if (!Permissions.has(sender, Permissions.RELOAD)) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        if (plugin.reloadAll()) {
            plugin.messages().send(sender, "reload-success");
        } else {
            plugin.messages().send(sender, "reload-failed");
        }
    }

    private void clearLeftovers(CommandSender sender, String label, boolean nearby) {
        if (!"osirisupgrader".equalsIgnoreCase(label)) {
            plugin.messages().send(sender, "clear-use-full");
            return;
        }
        if (!Permissions.has(sender, Permissions.CLEAR)) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        if (nearby && !(sender instanceof Player)) {
            plugin.messages().send(sender, "only-players");
            return;
        }
        int removed = nearby
                ? plugin.roulettes().purgeLeftovers(((Player) sender).getLocation(), 10.0d)
                : plugin.roulettes().purgeLeftovers(null, 0.0d);
        if (removed == 0) {
            plugin.messages().send(sender, "clear-none");
            return;
        }
        plugin.messages().send(sender, "clear-done", Map.of("count", Integer.toString(removed)));
    }

    private void rewards(CommandSender sender, String label) {
        if (!"osirisupgrader".equalsIgnoreCase(label)) {
            plugin.messages().send(sender, "rewards-use-full");
            return;
        }
        if (!Permissions.has(sender, Permissions.REWARDS)) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "only-players");
            return;
        }
        plugin.rewardEditor().openList(player);
    }

    private void base(CommandSender sender, String label) {
        if (!"osirisupgrader".equalsIgnoreCase(label)) {
            plugin.messages().send(sender, "base-use-full");
            return;
        }
        if (!Permissions.has(sender, Permissions.BASE)) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "only-players");
            return;
        }
        plugin.baseEditor().openList(player);
    }

    private void history(CommandSender sender, String label) {
        if (!"osirisupgrader".equalsIgnoreCase(label)) {
            plugin.messages().send(sender, "history-use-full");
            return;
        }
        if (!Permissions.has(sender, Permissions.HISTORY)) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        List<String> lines = plugin.history().latest();
        if (lines.isEmpty()) {
            plugin.messages().send(sender, "history-empty");
            return;
        }
        plugin.messages().send(sender, "history-header");
        for (String line : lines) {
            sender.sendMessage(Text.component("&7" + line));
        }
    }

    private void historyClear(CommandSender sender, String label) {
        if (!"osirisupgrader".equalsIgnoreCase(label)) {
            plugin.messages().send(sender, "historyclear-use-full");
            return;
        }
        if (!Permissions.has(sender, Permissions.HISTORY_CLEAR)) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        int removed = plugin.history().clear();
        if (removed == 0) {
            plugin.messages().send(sender, "history-clear-empty");
            return;
        }
        plugin.messages().send(sender, "history-cleared", Map.of("count", Integer.toString(removed)));
    }

    private void give(CommandSender sender, String[] args, boolean portable) {
        String permission = portable ? Permissions.GIVE : Permissions.SPAWNER;
        if (!Permissions.has(sender, permission)) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                plugin.messages().send(sender, "player-not-found");
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            plugin.messages().send(sender, "player-required", Map.of("command", portable ? "giveitem" : "spawner"));
            return;
        }
        var item = portable ? plugin.specialItems().portable() : plugin.specialItems().spawner();
        boolean dropped = ItemMover.giveOrDrop(target, item);
        plugin.messages().send(target, portable ? "giveitem-received" : "spawner-received");
        if (dropped) {
            plugin.messages().send(target, "inventory-full-drop");
        }
        if (!target.equals(sender)) {
            plugin.messages().send(sender, portable ? "giveitem-sent" : "spawner-sent", Map.of("player", target.getName()));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("help");
            if (Permissions.has(sender, Permissions.RELOAD)) {
                options.add("reload");
            }
            if (Permissions.has(sender, Permissions.GIVE)) {
                options.add("giveitem");
            }
            if (Permissions.has(sender, Permissions.SPAWNER)) {
                options.add("spawner");
            }
            if (Permissions.has(sender, Permissions.REWARDS) && "osirisupgrader".equalsIgnoreCase(alias)) {
                options.add("rewards");
            }
            if (Permissions.has(sender, Permissions.BASE) && "osirisupgrader".equalsIgnoreCase(alias)) {
                options.add("base");
            }
            if (Permissions.has(sender, Permissions.CLEAR) && "osirisupgrader".equalsIgnoreCase(alias)) {
                options.add("clear");
                options.add("clearnear");
            }
            if (Permissions.has(sender, Permissions.HISTORY) && "osirisupgrader".equalsIgnoreCase(alias)) {
                options.add("history");
            }
            if (Permissions.has(sender, Permissions.HISTORY_CLEAR) && "osirisupgrader".equalsIgnoreCase(alias)) {
                options.add("historyclear");
            }
            return filter(options, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("giveitem") || args[0].equalsIgnoreCase("spawner"))) {
            String permission = args[0].equalsIgnoreCase("giveitem") ? Permissions.GIVE : Permissions.SPAWNER;
            if (!Permissions.has(sender, permission)) {
                return List.of();
            }
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return filter(names, args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
