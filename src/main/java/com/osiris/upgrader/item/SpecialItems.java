package com.osiris.upgrader.item;

import com.osiris.upgrader.OsirisUpgraderPlugin;
import com.osiris.upgrader.config.Keys;
import com.osiris.upgrader.util.Models;
import com.osiris.upgrader.util.Text;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class SpecialItems {
    public static final String PORTABLE = "portable";
    public static final String SPAWNER = "spawner";

    private final OsirisUpgraderPlugin plugin;

    public SpecialItems(OsirisUpgraderPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack portable() {
        return create(
                plugin.configs().portableMaterial(),
                plugin.configs().portableModelData(),
                plugin.messages().raw("items.portable-name"),
                plugin.messages().list("items.portable-lore"),
                PORTABLE
        );
    }

    public ItemStack spawner() {
        return create(
                plugin.configs().spawnerMaterial(),
                plugin.configs().spawnerModelData(),
                plugin.messages().raw("items.spawner-name"),
                plugin.messages().list("items.spawner-lore"),
                SPAWNER
        );
    }

    public boolean isPortable(ItemStack stack) {
        return type(stack).equals(PORTABLE);
    }

    public boolean isSpawner(ItemStack stack) {
        return type(stack).equals(SPAWNER);
    }

    public boolean isSpecial(ItemStack stack) {
        return !type(stack).isEmpty();
    }

    private String type(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "";
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || Keys.ITEM_TYPE == null) {
            return "";
        }
        String value = meta.getPersistentDataContainer().get(Keys.ITEM_TYPE, PersistentDataType.STRING);
        return value == null ? "" : value;
    }

    private ItemStack create(Material material, int modelData, String name, List<String> lore, String type) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.displayName(Text.itemText(name));
            if (!lore.isEmpty()) {
                meta.lore(lore.stream().map(Text::itemText).toList());
            }
            if (modelData > 0) {
                Models.apply(meta, modelData);
            }
            meta.getPersistentDataContainer().set(Keys.ITEM_TYPE, PersistentDataType.STRING, type);
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS, org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
        });
        return stack;
    }
}
