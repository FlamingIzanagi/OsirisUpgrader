package com.osiris.upgrader.roulette;

import com.osiris.upgrader.OsirisUpgraderPlugin;
import com.osiris.upgrader.gui.RewardBrowserHolder;
import com.osiris.upgrader.gui.RewardEditHolder;
import com.osiris.upgrader.gui.RewardListHolder;
import com.osiris.upgrader.gui.UpgraderHolder;
import com.osiris.upgrader.item.ItemMover;
import com.osiris.upgrader.item.RewardEntry;
import com.osiris.upgrader.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class PendingSpins {
    private final OsirisUpgraderPlugin plugin;
    private final File file;
    private final Map<UUID, PendingSpin> spins = new LinkedHashMap<>();

    public PendingSpins(OsirisUpgraderPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pending-spins.yml");
    }

    public void load() {
        spins.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("spins");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            try {
                UUID id = UUID.fromString(key);
                UUID playerId = UUID.fromString(section.getString("player", ""));
                boolean win = section.getBoolean("win");
                double chance = section.getDouble("chance");
                int amount = section.getInt("amount");
                String rewardName = section.getString("reward-name", "");
                ItemStack reward = section.getItemStack("reward");
                String betName = section.getString("bet-name", "desconocida");
                int betAmount = Math.max(1, section.getInt("bet-amount", 1));
                int multiplier = Math.max(1, section.getInt("multiplier", 1));
                String rewardKey = section.getString("reward-key", "");
                boolean limitedHold = section.getBoolean("limited-hold", false);
                spins.put(id, new PendingSpin(id, playerId, win, chance, amount, rewardName, reward, betName, betAmount, multiplier, rewardKey, limitedHold));
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "Giro pendiente inválido: " + key, ex);
            }
        }
    }

    public void remember(PendingSpin spin) {
        spins.put(spin.id(), spin);
        save();
    }

    public PendingSpin claim(UUID id) {
        PendingSpin spin = spins.remove(id);
        if (spin != null) {
            save();
        }
        return spin;
    }

    public void restore(PendingSpin spin) {
        spins.put(spin.id(), spin);
        save();
    }

    public List<PendingSpin> forPlayer(UUID playerId) {
        List<PendingSpin> found = new ArrayList<>();
        for (PendingSpin spin : spins.values()) {
            if (spin.playerId().equals(playerId)) {
                found.add(spin);
            }
        }
        return found;
    }

    public void deliverOfflineReady(java.util.function.Predicate<UUID> stillRunning) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            deliver(player, stillRunning);
        }
    }

    public void deliver(Player player, java.util.function.Predicate<UUID> stillRunning) {
        for (PendingSpin spin : new ArrayList<>(forPlayer(player.getUniqueId()))) {
            if (stillRunning.test(spin.id())) {
                continue;
            }
            complete(spin.id(), player);
        }
    }

    public boolean complete(UUID id, Player online) {
        PendingSpin spin = claim(id);
        if (spin == null) {
            return false;
        }
        Player player = online != null ? online : Bukkit.getPlayer(spin.playerId());
        if (player == null || !player.isOnline()) {
            restore(spin);
            return false;
        }
        Map<String, String> placeholders = Map.of(
                "amount", Integer.toString(spin.amount()),
                "reward_name", spin.rewardName(),
                "chance", plugin.upgraderGui().chanceText(player, spin.chance())
        );
        if (spin.win() && spin.reward() != null) {
            boolean dropped = ItemMover.giveOrDrop(player, withAmount(spin.reward(), spin.amount()));
            plugin.messages().send(player, "spin-win", placeholders);
            plugin.sounds().play(player, "win", 1.0f);
            player.sendActionBar(plugin.messages().component("messages.actionbar-win", placeholders));
            if (dropped) {
                plugin.messages().send(player, "inventory-full-drop");
            }
        } else {
            plugin.messages().send(player, "spin-fail", placeholders);
            plugin.sounds().play(player, "fail", 1.0f);
            player.sendActionBar(plugin.messages().component("messages.actionbar-fail", placeholders));
        }
        settleLimited(spin);
        plugin.history().record(
                player.getName(),
                spin.betName(),
                spin.betAmount(),
                spin.multiplier(),
                spin.chance(),
                spin.win() && spin.reward() != null,
                spin.rewardName(),
                spin.amount()
        );
        announceDaily(player);
        return true;
    }

    private void settleLimited(PendingSpin spin) {
        if (!spin.limitedHold() || spin.rewardKey() == null || spin.rewardKey().isBlank()) {
            return;
        }
        RewardEntry entry = plugin.rewards().byKey(spin.rewardKey());
        if (spin.win() && spin.reward() != null) {
            plugin.rewards().commitLimited(entry);
            refreshRewardMenus();
        } else {
            plugin.rewards().releaseLimited(entry);
        }
    }

    private void refreshRewardMenus() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            Inventory top = online.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof RewardBrowserHolder browser) {
                plugin.browser().open(online, browser.session(), browser.page());
            } else if (top.getHolder() instanceof UpgraderHolder wheel && !wheel.isSpinning()) {
                plugin.upgraderGui().paint(wheel);
            } else if (top.getHolder() instanceof RewardListHolder) {
                plugin.rewardEditor().paintList(top);
            } else if (top.getHolder() instanceof RewardEditHolder edit) {
                RewardEntry current = plugin.rewards().byKey(edit.key());
                if (current == null) {
                    plugin.rewardEditor().openList(online);
                } else {
                    plugin.rewardEditor().openEdit(online, current);
                }
            }
        }
    }

    private void announceDaily(Player player) {
        if (!plugin.configs().broadcastDailySpin()) {
            return;
        }
        String text = plugin.messages().format("messages.daily-spin", Map.of("player", player.getName()));
        if (plugin.configs().dailySpinPrefix()) {
            text = plugin.messages().format("messages.prefix", Map.of()) + text;
        }
        Bukkit.broadcast(Text.component(text));
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("spins");
        for (PendingSpin spin : spins.values()) {
            ConfigurationSection section = root.createSection(spin.id().toString());
            section.set("player", spin.playerId().toString());
            section.set("win", spin.win());
            section.set("chance", spin.chance());
            section.set("amount", spin.amount());
            section.set("reward-name", spin.rewardName());
            section.set("bet-name", spin.betName());
            section.set("bet-amount", spin.betAmount());
            section.set("multiplier", spin.multiplier());
            section.set("reward-key", spin.rewardKey());
            section.set("limited-hold", spin.limitedHold());
            if (spin.reward() != null) {
                section.set("reward", spin.reward());
            }
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo guardar pending-spins.yml", ex);
        }
    }

    private static ItemStack withAmount(ItemStack template, int amount) {
        ItemStack copy = template.clone();
        copy.setAmount(Math.max(1, amount));
        return copy;
    }

    public record PendingSpin(
            UUID id,
            UUID playerId,
            boolean win,
            double chance,
            int amount,
            String rewardName,
            ItemStack reward,
            String betName,
            int betAmount,
            int multiplier,
            String rewardKey,
            boolean limitedHold
    ) {
    }
}
