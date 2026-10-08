package com.osiris.upgrader.gui;

import com.osiris.upgrader.util.Models;
import com.osiris.upgrader.util.Text;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class GuiItems {
    private GuiItems() {
    }

    public static ItemStack of(Material material, String name, List<String> lore, boolean glow, int amount) {
        int safeAmount = Math.max(1, Math.min(Math.max(1, amount), Math.max(1, material.getMaxStackSize())));
        ItemStack stack = new ItemStack(material, safeAmount);
        stack.editMeta(meta -> {
            if (name != null) {
                meta.displayName(Text.itemText(name));
            }
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore.stream().map(Text::itemText).toList());
            }
            if (glow) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        });
        return stack;
    }

    public static ItemStack of(ItemStack base, Material fallback, String name, List<String> lore, boolean glow, int amount, int modelData) {
        ItemStack stack = base == null ? new ItemStack(fallback == null ? Material.PAPER : fallback) : base.clone();
        int max = Math.max(1, stack.getMaxStackSize());
        stack.setAmount(Math.max(1, Math.min(Math.max(1, amount), max)));
        stack.editMeta(meta -> {
            if (name != null) {
                meta.displayName(Text.itemText(name));
            }
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore.stream().map(Text::itemText).toList());
            }
            if (glow) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            if (modelData > 0) {
                Models.apply(meta, modelData);
            }
        });
        return stack;
    }

    public static ItemStack filler(Material material, int modelData, String name, boolean hideTooltip) {
        ItemStack stack = new ItemStack(material == null ? Material.BLACK_STAINED_GLASS_PANE : material);
        stack.editMeta(meta -> {
            meta.displayName(Text.itemText(name == null || name.isEmpty() ? " " : name));
            if (hideTooltip) {
                meta.setHideTooltip(true);
            }
            if (modelData > 0) {
                Models.apply(meta, modelData);
            }
        });
        return stack;
    }
}
