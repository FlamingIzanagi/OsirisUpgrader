package com.osiris.upgrader.item;

import com.osiris.upgrader.OsirisUpgraderPlugin;
import com.osiris.upgrader.config.ConfigManager;
import com.osiris.upgrader.hook.NexoBridge;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ItemValueService {
    private final OsirisUpgraderPlugin plugin;
    private Map<Material, Double> vanilla = new EnumMap<>(Material.class);
    private final Map<String, Double> nexo = new java.util.HashMap<>();
    private final Map<String, Double> custom = new java.util.HashMap<>();
    private Map<Material, Integer> vanillaLimits = new EnumMap<>(Material.class);
    private final Map<String, Integer> nexoLimits = new java.util.HashMap<>();
    private final Map<String, Integer> customLimits = new java.util.HashMap<>();
    private Set<Material> blocked = Set.of();
    private double defaultVanilla = 1.0d;
    private double defaultNexo = 25.0d;
    private double defaultCustom = 25.0d;

    public ItemValueService(OsirisUpgraderPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        ConfigManager configs = plugin.configs();
        ConfigurationSection root = configs.items();
        defaultVanilla = root.getDouble("defaults.vanilla", 1.0d);
        defaultNexo = root.getDouble("defaults.nexo", 25.0d);
        defaultCustom = root.getDouble("defaults.custom", defaultNexo);
        Set<Material> blockedMaterials = new HashSet<>();
        for (String raw : root.getStringList("blocked-materials")) {
            Material material = Material.matchMaterial(raw);
            if (material != null) {
                blockedMaterials.add(material);
            } else {
                plugin.getLogger().warning("Material bloqueado desconocido: " + raw);
            }
        }
        blocked = Set.copyOf(blockedMaterials);
        Map<Material, Double> vanillaValues = new EnumMap<>(Material.class);
        Map<Material, Integer> vanillaCaps = new EnumMap<>(Material.class);
        ConfigurationSection vanillaSection = root.getConfigurationSection("item-values.vanilla");
        if (vanillaSection != null) {
            for (String key : vanillaSection.getKeys(false)) {
                Material material = materialOf(key);
                if (material == null || !material.isItem()) {
                    plugin.getLogger().warning("Material desconocido en vanilla-items.yml: " + key);
                    continue;
                }
                vanillaValues.put(material, readValue(vanillaSection, key));
                int cap = readLimit(vanillaSection, key);
                if (cap > 0) {
                    vanillaCaps.put(material, cap);
                }
            }
        }
        nexo.clear();
        nexoLimits.clear();
        custom.clear();
        customLimits.clear();
        ConfigurationSection nexoSection = root.getConfigurationSection("item-values.nexo");
        if (nexoSection != null) {
            for (String key : nexoSection.getKeys(false)) {
                String id = NexoBridge.normalize(key);
                nexo.put(id, readValue(nexoSection, key));
                int cap = readLimit(nexoSection, key);
                if (cap > 0) {
                    nexoLimits.put(id, cap);
                }
            }
        }
        ConfigurationSection customSection = root.getConfigurationSection("item-values.custom");
        if (customSection != null) {
            for (String key : customSection.getKeys(false)) {
                custom.put(key, readValue(customSection, key));
                int cap = readLimit(customSection, key);
                if (cap > 0) {
                    customLimits.put(key, cap);
                }
            }
        }
        this.vanilla = vanillaValues;
        this.vanillaLimits = vanillaCaps;
        plugin.getLogger().info("Valores cargados: " + vanillaValues.size() + " vanilla, " + nexo.size()
                + " nexo, " + custom.size() + " personalizados. El resto de ítems vanilla usa " + defaultVanilla + ".");
    }

    public Map<Material, Double> vanillaEntries() {
        return Map.copyOf(vanilla);
    }

    public double unitValue(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || plugin.specialItems().isSpecial(stack)) {
            return 0.0d;
        }
        double base = baseValue(stack);
        if (base <= 0.0d) {
            return 0.0d;
        }
        return base * damageScale(stack);
    }

    public int maxWin(RewardEntry entry) {
        if (entry == null) {
            return 0;
        }
        if (entry.snapshot()) {
            return customLimits.getOrDefault(entry.key(), 0);
        }
        if (entry.nexo()) {
            return nexoLimits.getOrDefault(NexoBridge.normalize(entry.id()), 0);
        }
        if (entry.material() == null) {
            return 0;
        }
        return vanillaLimits.getOrDefault(entry.material(), 0);
    }

    public void setUnitValue(RewardEntry entry, double value) {
        if (entry == null) {
            return;
        }
        store(entry, value, maxWin(entry));
    }

    public void setMaxWin(RewardEntry entry, int limit) {
        if (entry == null) {
            return;
        }
        store(entry, unitValue(entry), Math.max(0, limit));
    }

    public boolean isBlocked(Material material) {
        return material != null && blocked.contains(material);
    }

    public double unitValue(RewardEntry entry) {
        if (entry == null) {
            return 0.0d;
        }
        if (entry.snapshot()) {
            Double configured = custom.get(entry.key());
            return configured != null ? configured : defaultCustom;
        }
        if (entry.nexo()) {
            Double configured = nexo.get(NexoBridge.normalize(entry.id()));
            return configured != null ? configured : defaultNexo;
        }
        if (entry.material() == null || blocked.contains(entry.material())) {
            return 0.0d;
        }
        Double configured = vanilla.get(entry.material());
        return configured != null ? configured : defaultVanilla;
    }

    private void store(RewardEntry entry, double value, int limit) {
        String path = entry.snapshot()
                ? "item-values.custom." + entry.key()
                : entry.nexo()
                ? "item-values.nexo." + NexoBridge.normalize(entry.id())
                : "item-values.vanilla." + entry.material().name();
        org.bukkit.configuration.file.FileConfiguration yaml = plugin.configs().items();
        yaml.set(path, null);
        if (limit > 0) {
            yaml.set(path + ".value", value);
            yaml.set(path + ".max-win", limit);
        } else {
            yaml.set(path, value);
        }
        if (entry.snapshot()) {
            custom.put(entry.key(), value);
            if (limit > 0) {
                customLimits.put(entry.key(), limit);
            } else {
                customLimits.remove(entry.key());
            }
        } else if (entry.nexo()) {
            String id = NexoBridge.normalize(entry.id());
            nexo.put(id, value);
            if (limit > 0) {
                nexoLimits.put(id, limit);
            } else {
                nexoLimits.remove(id);
            }
        } else if (entry.material() != null) {
            vanilla.put(entry.material(), value);
            if (limit > 0) {
                vanillaLimits.put(entry.material(), limit);
            } else {
                vanillaLimits.remove(entry.material());
            }
        }
        plugin.configs().saveFile("vanilla-items.yml");
    }

    private static Material materialOf(String key) {
        Material material = Material.matchMaterial(key);
        if (material != null) {
            return material;
        }
        String modern = switch (key) {
            case "RAW_BEEF" -> "BEEF";
            case "RAW_PORKCHOP" -> "PORKCHOP";
            case "RAW_CHICKEN" -> "CHICKEN";
            default -> null;
        };
        return modern == null ? null : Material.matchMaterial(modern);
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

    private double baseValue(ItemStack stack) {
        if (plugin.baseValues() != null) {
            Double listed = plugin.baseValues().valueOf(stack);
            if (listed != null) {
                return listed;
            }
        }
        String nexoId = plugin.nexo().idFromItem(stack);
        if (nexoId != null) {
            Double configured = nexo.get(NexoBridge.normalize(nexoId));
            return configured != null ? configured : defaultNexo;
        }
        if (blocked.contains(stack.getType())) {
            return 0.0d;
        }
        if (ItemSnapshots.isCustom(stack)) {
            return 0.0d;
        }
        Double configured = vanilla.get(stack.getType());
        return configured != null ? configured : defaultVanilla;
    }

    private double damageScale(ItemStack stack) {
        if (!plugin.configs().scaleDamagedTools() || stack.getType().getMaxDurability() <= 0) {
            return 1.0d;
        }
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return 1.0d;
        }
        int max = stack.getType().getMaxDurability();
        double remaining = max - damageable.getDamage();
        return Math.max(0.0d, remaining / max);
    }
}
