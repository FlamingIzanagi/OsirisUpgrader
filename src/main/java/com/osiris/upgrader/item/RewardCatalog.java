package com.osiris.upgrader.item;

import com.osiris.upgrader.OsirisUpgraderPlugin;
import com.osiris.upgrader.hook.NexoBridge;
import com.osiris.upgrader.util.Models;
import com.osiris.upgrader.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

public final class RewardCatalog {
    private final OsirisUpgraderPlugin plugin;
    private final List<RewardEntry> entries = new ArrayList<>();
    private final java.util.Map<String, Double> prizeValues = new java.util.HashMap<>();
    private final java.util.Map<String, Integer> prizeLimits = new java.util.HashMap<>();
    private final java.util.Map<String, Integer> limited = new java.util.HashMap<>();
    private final java.util.Map<String, Integer> holds = new java.util.HashMap<>();
    private final java.util.Map<String, Boolean> glow = new java.util.HashMap<>();

    public RewardCatalog(OsirisUpgraderPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        Logger logger = plugin.getLogger();
        ConfigurationSection section = plugin.configs().rewards().getConfigurationSection("rewards");
        entries.clear();
        prizeValues.clear();
        prizeLimits.clear();
        limited.clear();
        holds.clear();
        glow.clear();
        java.util.List<String> spent = new ArrayList<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection reward = section.getConfigurationSection(key);
                if (reward == null) {
                    continue;
                }
                if (reward.isSet("limited") && reward.getInt("limited") <= 0) {
                    spent.add(key);
                    continue;
                }
                String type = reward.getString("type", "vanilla").trim().toLowerCase();
                String displayName = reward.getString("display-name");
                int modelData = reward.getInt("custom-model-data", 0);
                if (reward.isSet("value")) {
                    prizeValues.put(key, reward.getDouble("value"));
                }
                if (reward.isSet("max-win")) {
                    prizeLimits.put(key, Math.max(0, reward.getInt("max-win")));
                }
                if (reward.isSet("limited")) {
                    limited.put(key, Math.max(1, reward.getInt("limited")));
                }
                if (reward.getBoolean("glow", false)) {
                    glow.put(key, true);
                }
                if (type.equals("nexo")) {
                    String id = reward.getString("id", "");
                    if (id.isBlank()) {
                        logger.warning("Recompensa Nexo sin id: " + key);
                        continue;
                    }
                    String data = reward.getString("item", "");
                    entries.add(new RewardEntry(key, true, id, null, displayName, modelData, data.isBlank() ? null : data));
                    continue;
                }
                if (type.equals("item")) {
                    String data = reward.getString("item", "");
                    if (data.isBlank()) {
                        logger.warning("Premio personalizado sin datos: " + key);
                        continue;
                    }
                    String materialName = reward.getString("material", "");
                    Material material = Material.matchMaterial(materialName == null ? "" : materialName);
                    entries.add(new RewardEntry(key, false, material == null ? key : material.name(), material, displayName, modelData, data));
                    continue;
                }
                String materialName = reward.getString("material", reward.getString("id", ""));
                Material material = Material.matchMaterial(materialName);
                if (material == null || !material.isItem() || material.isAir()) {
                    logger.warning("Recompensa vanilla inválida (" + key + "): " + materialName);
                    continue;
                }
                entries.add(new RewardEntry(key, false, material.name(), material, displayName, modelData, null));
            }
        }
        if (!spent.isEmpty()) {
            for (String key : spent) {
                plugin.configs().rewards().set("rewards." + key, null);
            }
            plugin.configs().saveFile("rewards.yml");
            logger.info("Se quitaron " + spent.size() + " premios limitados agotados.");
        }
        adoptCustomValues();
        logger.info("Recompensas cargadas: " + entries.size());
    }

    public boolean isLimited(RewardEntry entry) {
        return entry != null && limited.containsKey(entry.key());
    }

    public int limitedRemaining(RewardEntry entry) {
        if (!isLimited(entry)) {
            return 0;
        }
        return Math.max(0, limited.getOrDefault(entry.key(), 0));
    }

    public boolean available(RewardEntry entry) {
        if (!isLimited(entry)) {
            return true;
        }
        int free = limited.getOrDefault(entry.key(), 0) - holds.getOrDefault(entry.key(), 0);
        return free >= 1;
    }

    public boolean tryHold(RewardEntry entry) {
        if (!isLimited(entry)) {
            return true;
        }
        if (!available(entry)) {
            return false;
        }
        holds.merge(entry.key(), 1, Integer::sum);
        return true;
    }

    public void releaseLimited(RewardEntry entry) {
        if (entry == null) {
            return;
        }
        Integer held = holds.get(entry.key());
        if (held == null || held <= 0) {
            return;
        }
        if (held == 1) {
            holds.remove(entry.key());
        } else {
            holds.put(entry.key(), held - 1);
        }
    }

    public void commitLimited(RewardEntry entry) {
        if (entry == null || !isLimited(entry)) {
            releaseLimited(entry);
            return;
        }
        releaseLimited(entry);
        int left = limited.getOrDefault(entry.key(), 0) - 1;
        if (left <= 0) {
            plugin.getLogger().info("Premio limitado agotado: " + entry.key());
            remove(entry.key());
            return;
        }
        limited.put(entry.key(), left);
        plugin.configs().rewards().set("rewards." + entry.key() + ".limited", left);
        plugin.configs().saveFile("rewards.yml");
    }

    public void setLimited(RewardEntry entry, int amount) {
        if (entry == null) {
            return;
        }
        if (amount <= 0) {
            limited.remove(entry.key());
            plugin.configs().rewards().set("rewards." + entry.key() + ".limited", null);
            plugin.configs().saveFile("rewards.yml");
            return;
        }
        limited.put(entry.key(), amount);
        prizeLimits.remove(entry.key());
        plugin.configs().rewards().set("rewards." + entry.key() + ".limited", amount);
        plugin.configs().rewards().set("rewards." + entry.key() + ".max-win", null);
        plugin.configs().saveFile("rewards.yml");
    }

    public boolean glow(RewardEntry entry) {
        return entry != null && glow.getOrDefault(entry.key(), false);
    }

    public void toggleGlow(RewardEntry entry) {
        if (entry == null) {
            return;
        }
        boolean next = !glow.getOrDefault(entry.key(), false);
        if (next) {
            glow.put(entry.key(), true);
            plugin.configs().rewards().set("rewards." + entry.key() + ".glow", true);
        } else {
            glow.remove(entry.key());
            plugin.configs().rewards().set("rewards." + entry.key() + ".glow", null);
        }
        plugin.configs().saveFile("rewards.yml");
    }

    public double prizeValue(RewardEntry entry) {
        if (entry == null) {
            return 0.0d;
        }
        Double own = prizeValues.get(entry.key());
        if (own != null) {
            return own;
        }
        return plugin.itemValues().unitValue(entry);
    }

    public void setPrizeValue(RewardEntry entry, double value) {
        if (entry == null) {
            return;
        }
        prizeValues.put(entry.key(), value);
        plugin.configs().rewards().set("rewards." + entry.key() + ".value", value);
        plugin.configs().saveFile("rewards.yml");
    }

    public int prizeLimit(RewardEntry entry) {
        if (entry == null) {
            return 0;
        }
        if (isLimited(entry)) {
            return 1;
        }
        Integer own = prizeLimits.get(entry.key());
        if (own != null) {
            return own;
        }
        return plugin.itemValues().maxWin(entry);
    }

    public void setPrizeLimit(RewardEntry entry, int limit) {
        if (entry == null) {
            return;
        }
        int stored = Math.max(0, limit);
        prizeLimits.put(entry.key(), stored);
        plugin.configs().rewards().set("rewards." + entry.key() + ".max-win", stored);
        plugin.configs().saveFile("rewards.yml");
    }

    public List<RewardEntry> all() {
        return List.copyOf(entries);
    }

    public RewardEntry byKey(String key) {
        if (key == null) {
            return null;
        }
        for (RewardEntry entry : entries) {
            if (entry.key().equalsIgnoreCase(key)) {
                return entry;
            }
        }
        return null;
    }

    public RewardEntry matching(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        ItemStack one = stack.clone();
        one.setAmount(1);
        String nexoId = plugin.nexo().idFromItem(stack);
        boolean custom = nexoId != null || ItemSnapshots.isCustom(stack);
        if (nexoId != null) {
            String normalized = NexoBridge.normalize(nexoId);
            for (RewardEntry entry : entries) {
                if (!entry.nexo() || !NexoBridge.normalize(entry.id()).equals(normalized)) {
                    continue;
                }
                ItemStack shown = shownItem(entry);
                if (sameItem(shown, one)) {
                    return entry;
                }
            }
        }
        if (custom) {
            for (RewardEntry entry : entries) {
                if (!entry.snapshot()) {
                    continue;
                }
                ItemStack stored = ItemSnapshots.restore(entry.itemData());
                if (sameItem(stored, one)) {
                    return entry;
                }
            }
            return null;
        }
        for (RewardEntry entry : entries) {
            if (!entry.nexo() && !entry.snapshot() && entry.material() == stack.getType()) {
                return entry;
            }
        }
        return null;
    }

    public RewardEntry add(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || matching(stack) != null) {
            return null;
        }
        String nexoId = plugin.nexo().idFromItem(stack);
        RewardEntry entry;
        if (nexoId != null) {
            String bare = NexoBridge.strip(nexoId);
            entry = new RewardEntry(
                    uniqueKey(bare),
                    true,
                    "nexo:" + bare,
                    stack.getType(),
                    displayName(stack),
                    modelData(stack),
                    ItemSnapshots.capture(stack)
            );
        } else if (ItemSnapshots.isCustom(stack)) {
            String label = Text.plainName(stack);
            entry = new RewardEntry(
                    uniqueKey(label == null ? stack.getType().name() : label),
                    false,
                    stack.getType().name(),
                    stack.getType(),
                    displayName(stack),
                    modelData(stack),
                    ItemSnapshots.capture(stack)
            );
        } else {
            entry = new RewardEntry(
                    uniqueKey(stack.getType().name().toLowerCase(Locale.ROOT)),
                    false,
                    stack.getType().name(),
                    stack.getType(),
                    displayName(stack),
                    modelData(stack),
                    null
            );
        }
        entries.add(entry);
        write(entry);
        plugin.configs().saveFile("rewards.yml");
        return entry;
    }

    public boolean remove(String key) {
        RewardEntry entry = byKey(key);
        if (entry == null) {
            return false;
        }
        entries.remove(entry);
        prizeValues.remove(entry.key());
        prizeLimits.remove(entry.key());
        limited.remove(entry.key());
        holds.remove(entry.key());
        glow.remove(entry.key());
        plugin.configs().rewards().set("rewards." + entry.key(), null);
        plugin.configs().saveFile("rewards.yml");
        return true;
    }

    private void adoptCustomValues() {
        org.bukkit.configuration.file.FileConfiguration items = plugin.configs().items();
        boolean moved = false;
        for (RewardEntry entry : entries) {
            if (prizeValues.containsKey(entry.key())) {
                continue;
            }
            String path = "item-values.custom." + entry.key();
            if (!items.isSet(path)) {
                continue;
            }
            double value = items.isConfigurationSection(path)
                    ? items.getDouble(path + ".value")
                    : items.getDouble(path);
            prizeValues.put(entry.key(), value);
            plugin.configs().rewards().set("rewards." + entry.key() + ".value", value);
            items.set(path, null);
            moved = true;
        }
        if (!moved) {
            return;
        }
        plugin.configs().saveFile("rewards.yml");
        plugin.configs().saveFile("vanilla-items.yml");
        plugin.itemValues().reload();
    }

    private void write(RewardEntry entry) {
        String path = "rewards." + entry.key();
        ConfigurationSection section = plugin.configs().rewards().createSection(path);
        if (entry.nexo()) {
            section.set("type", "nexo");
            section.set("id", entry.id());
            if (entry.material() != null) {
                section.set("material", entry.material().name());
            }
            if (entry.snapshot()) {
                section.set("item", entry.itemData());
            }
        } else if (entry.snapshot()) {
            section.set("type", "item");
            if (entry.material() != null) {
                section.set("material", entry.material().name());
            }
            section.set("item", entry.itemData());
        } else {
            section.set("type", "vanilla");
            section.set("material", entry.material().name());
        }
        if (entry.displayName() != null && !entry.displayName().isBlank()) {
            section.set("display-name", entry.displayName());
        }
        if (entry.customModelData() > 0) {
            section.set("custom-model-data", entry.customModelData());
        }
    }

    private String uniqueKey(String base) {
        String key = base == null ? "" : base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
        if (key.isBlank() || key.equals("_")) {
            key = "premio";
        }
        String candidate = key;
        int suffix = 2;
        while (byKey(candidate) != null) {
            candidate = key + "_" + suffix++;
        }
        return candidate;
    }

    private static boolean sameItem(ItemStack left, ItemStack right) {
        if (left == null || right == null || left.getType() != right.getType()) {
            return false;
        }
        try {
            ItemStack first = left.clone();
            ItemStack second = right.clone();
            first.setAmount(1);
            second.setAmount(1);
            return java.util.Arrays.equals(first.serializeAsBytes(), second.serializeAsBytes());
        } catch (RuntimeException ex) {
            return left.isSimilar(right);
        }
    }

    private ItemStack shownItem(RewardEntry entry) {
        if (entry.snapshot()) {
            ItemStack stored = ItemSnapshots.restore(entry.itemData());
            if (stored != null) {
                return stored;
            }
        }
        return createTemplate(entry);
    }

    private static String displayName(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || meta.displayName() == null) {
            return null;
        }
        return Text.legacy(meta.displayName());
    }

    private static int modelData(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return 0;
        }
        CustomModelDataComponent component = meta.getCustomModelDataComponent();
        if (component == null || component.getFloats() == null || component.getFloats().isEmpty()) {
            return 0;
        }
        return Math.round(component.getFloats().getFirst());
    }

    public List<RewardEntry> viewable() {
        List<RewardEntry> visible = new ArrayList<>();
        for (RewardEntry entry : entries) {
            if (isLimited(entry) && limitedRemaining(entry) <= 0) {
                continue;
            }
            try {
                if (createTemplate(entry) != null) {
                    visible.add(entry);
                }
            } catch (Throwable ex) {
                plugin.getLogger().warning("No se pudo preparar el premio " + entry.key() + ": " + ex.getClass().getSimpleName());
            }
        }
        return Collections.unmodifiableList(visible);
    }

    public ItemStack createTemplate(RewardEntry entry) {
        if (entry == null) {
            return null;
        }
        if (entry.snapshot()) {
            ItemStack stored = ItemSnapshots.restore(entry.itemData());
            if (stored != null) {
                return finish(stored, entry);
            }
        }
        if (entry.nexo()) {
            if (!plugin.nexo().isHooked()) {
                return null;
            }
            ItemStack stack = plugin.nexo().createItem(entry.id());
            if (stack == null) {
                return null;
            }
            stack.setAmount(1);
            return finish(stack, entry);
        }
        ItemStack stack = new ItemStack(entry.material());
        if (entry.displayName() != null || entry.customModelData() > 0) {
            stack.editMeta(meta -> {
                if (entry.displayName() != null) {
                    meta.displayName(Text.itemText(entry.displayName()));
                }
                if (entry.customModelData() > 0) {
                    Models.apply(meta, entry.customModelData());
                }
            });
        }
        return finish(stack, entry);
    }

    private ItemStack finish(ItemStack stack, RewardEntry entry) {
        if (stack == null || !glow(entry)) {
            return stack;
        }
        stack.editMeta(meta -> {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        });
        return stack;
    }

    public String nameOf(RewardEntry entry) {
        if (entry == null) {
            return plugin.messages().raw("gui-texts.none");
        }
        if (entry.displayName() != null && !entry.displayName().isBlank()) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(Text.component(entry.displayName()));
        }
        if (entry.nexo()) {
            return NexoBridge.strip(entry.id());
        }
        return Text.prettify(entry.id());
    }

    public Component showcaseLabel(RewardEntry entry) {
        String template = plugin.messages().raw("messages.showcase-label");
        if (template == null || template.isBlank() || "messages.showcase-label".equals(template)) {
            template = "&6%reward_name%";
        }
        return Text.withToken(template, "reward_name", nameComponent(entry));
    }

    private Component nameComponent(RewardEntry entry) {
        if (entry == null) {
            return Component.text(nameOf(null));
        }
        ItemStack stack = createTemplate(entry);
        if (stack != null) {
            return Text.itemTitle(stack);
        }
        if (entry.displayName() != null && !entry.displayName().isBlank()) {
            return Text.component(entry.displayName());
        }
        if (entry.material() != null) {
            return Component.translatable(entry.material().translationKey());
        }
        return Component.text(nameOf(entry));
    }
}
