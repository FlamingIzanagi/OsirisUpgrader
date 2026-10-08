package com.osiris.upgrader.item;

import com.osiris.upgrader.OsirisUpgraderPlugin;
import com.osiris.upgrader.hook.NexoBridge;
import com.osiris.upgrader.util.Text;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

public final class BaseValueCatalog {
    private final OsirisUpgraderPlugin plugin;
    private final List<Entry> entries = new ArrayList<>();

    public BaseValueCatalog(OsirisUpgraderPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        entries.clear();
        File legacy = new File(plugin.getDataFolder(), "base-values.yml");
        File current = file();
        if (legacy.isFile() && !current.exists()) {
            if (legacy.renameTo(current)) {
                plugin.getLogger().info("base-values.yml ahora se llama custom-items.yml.");
            } else {
                plugin.getLogger().warning("No se pudo renombrar base-values.yml a custom-items.yml.");
            }
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file());
        ConfigurationSection section = yaml.getConfigurationSection("entries");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String data = section.getString(key + ".item", "");
                ItemStack item = ItemSnapshots.restore(data);
                if (item == null) {
                    plugin.getLogger().warning("Valor base ilegible: " + key);
                    continue;
                }
                entries.add(new Entry(key, data, section.getDouble(key + ".value"), item));
            }
        }
        importLegacyNexo();
        plugin.getLogger().info("Valores base de ítems custom: " + entries.size());
    }

    public List<Entry> all() {
        return List.copyOf(entries);
    }

    public Entry byKey(String key) {
        if (key == null) {
            return null;
        }
        for (Entry entry : entries) {
            if (entry.key().equalsIgnoreCase(key)) {
                return entry;
            }
        }
        return null;
    }

    public Double valueOf(ItemStack stack) {
        Entry entry = matching(stack);
        return entry == null ? null : entry.value();
    }

    public Entry matching(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        String nexoId = plugin.nexo().idFromItem(stack);
        if (nexoId != null) {
            String normalized = NexoBridge.normalize(nexoId);
            for (Entry entry : entries) {
                String stored = plugin.nexo().idFromItem(entry.item());
                if (stored != null && NexoBridge.normalize(stored).equals(normalized)) {
                    return entry;
                }
            }
        }
        if (nexoId == null && !ItemSnapshots.isCustom(stack)) {
            return null;
        }
        ItemStack one = stack.clone();
        one.setAmount(1);
        for (Entry entry : entries) {
            if (entry.item() != null && entry.item().isSimilar(one)) {
                return entry;
            }
        }
        return null;
    }

    public Entry add(ItemStack stack, double value) {
        if (stack == null || matching(stack) != null) {
            return null;
        }
        String label = Text.plainName(stack);
        Entry entry = new Entry(
                uniqueKey(label == null ? stack.getType().name() : label),
                ItemSnapshots.capture(stack),
                value,
                copy(stack)
        );
        entries.add(entry);
        save();
        return entry;
    }

    public void setValue(String key, double value) {
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            if (!entry.key().equalsIgnoreCase(key)) {
                continue;
            }
            entries.set(index, new Entry(entry.key(), entry.itemData(), value, entry.item()));
            save();
            return;
        }
    }

    public boolean remove(String key) {
        Entry entry = byKey(key);
        if (entry == null) {
            return false;
        }
        entries.remove(entry);
        save();
        return true;
    }

    private void importLegacyNexo() {
        FileConfiguration items = plugin.configs().items();
        ConfigurationSection section = items.getConfigurationSection("item-values.nexo");
        if (section == null) {
            return;
        }
        List<String> converted = new ArrayList<>();
        boolean changed = false;
        for (String key : new ArrayList<>(section.getKeys(false))) {
            ItemStack stack = plugin.nexo().createItem(key);
            if (stack == null) {
                plugin.getLogger().warning("No se pudo pasar " + key + " al editor base: Nexo no devolvió el ítem.");
                continue;
            }
            double value = readValue(section, key);
            int limit = readLimit(section, key);
            if (matching(stack) == null) {
                String label = Text.plainName(stack);
                entries.add(new Entry(
                        uniqueKey(label == null ? key : label),
                        ItemSnapshots.capture(stack),
                        value,
                        copy(stack)
                ));
                changed = true;
            }
            if (limit > 0) {
                String normalized = NexoBridge.normalize(key);
                for (RewardEntry reward : plugin.rewards().all()) {
                    if (reward.nexo() && NexoBridge.normalize(reward.id()).equals(normalized)) {
                        plugin.rewards().setPrizeLimit(reward, limit);
                    }
                }
            }
            items.set("item-values.nexo." + key, null);
            converted.add(key);
        }
        ConfigurationSection left = items.getConfigurationSection("item-values.nexo");
        if (left == null || left.getKeys(false).isEmpty()) {
            items.set("item-values.nexo", null);
        }
        if (converted.isEmpty()) {
            return;
        }
        plugin.configs().saveFile("vanilla-items.yml");
        if (changed) {
            save();
        }
        plugin.itemValues().reload();
        plugin.getLogger().info("Se pasaron " + converted.size() + " valores de Nexo a custom-items.yml y se quitaron de vanilla-items.yml.");
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of("Valor base para apostar ítems custom. Se edita con /osirisupgrader base."));
        for (Entry entry : entries) {
            String path = "entries." + entry.key();
            yaml.set(path + ".value", entry.value());
            yaml.set(path + ".item", entry.itemData());
        }
        try {
            yaml.save(file());
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo guardar custom-items.yml", ex);
        }
    }

    private File file() {
        return new File(plugin.getDataFolder(), "custom-items.yml");
    }

    private String uniqueKey(String base) {
        String key = base == null ? "" : base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
        if (key.isBlank() || key.equals("_")) {
            key = "item";
        }
        String candidate = key;
        int suffix = 2;
        while (byKey(candidate) != null) {
            candidate = key + "_" + suffix++;
        }
        return candidate;
    }

    private static ItemStack copy(ItemStack stack) {
        ItemStack one = stack.clone();
        one.setAmount(1);
        return one;
    }

    private static double readValue(ConfigurationSection section, String key) {
        ConfigurationSection nested = section.getConfigurationSection(key);
        if (nested != null) {
            return nested.getDouble("value", 0.0d);
        }
        return section.getDouble(key, 0.0d);
    }

    private static int readLimit(ConfigurationSection section, String key) {
        ConfigurationSection nested = section.getConfigurationSection(key);
        if (nested == null) {
            return 0;
        }
        return Math.max(0, nested.getInt("max-win", 0));
    }

    public record Entry(String key, String itemData, double value, ItemStack item) {
    }
}
