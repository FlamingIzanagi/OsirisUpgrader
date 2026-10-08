package com.osiris.upgrader.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Base64;
import java.util.Objects;

public final class ItemSnapshots {
    private ItemSnapshots() {
    }

    public static boolean isCustom(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.getType().isItem()) {
            return false;
        }
        ItemStack copy = stack.clone();
        copy.setAmount(1);
        ItemStack vanilla = ItemStack.of(stack.getType());
        if (sameVanillaItem(copy, vanilla)) {
            return false;
        }
        ItemMeta meta = copy.getItemMeta();
        ItemMeta base = vanilla.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (meta.hasLore() && (base == null || !meta.hasLore() || base.lore() == null || !base.lore().equals(meta.lore()))) {
            return true;
        }
        if (meta.hasEnchants() && (base == null || !meta.getEnchants().equals(base.getEnchants()))) {
            return true;
        }
        if (hasOwnModel(meta, base)) {
            return true;
        }
        return !meta.getPersistentDataContainer().isEmpty();
    }

    private static boolean sameVanillaItem(ItemStack stack, ItemStack vanilla) {
        if (stack.isSimilar(vanilla)) {
            return true;
        }
        ItemStack stripped = stack.clone();
        stripped.editMeta(meta -> {
            ItemMeta base = vanilla.getItemMeta();
            meta.customName(base == null ? null : base.customName());
            meta.itemName(base == null ? null : base.itemName());
        });
        return stripped.isSimilar(vanilla);
    }

    private static boolean hasOwnModel(ItemMeta meta, ItemMeta base) {
        if (meta.hasCustomModelData() && (base == null || !base.hasCustomModelData()
                || !Objects.equals(meta.getCustomModelData(), base.getCustomModelData()))) {
            return true;
        }
        return meta.hasItemModel() && (base == null || !Objects.equals(meta.getItemModel(), base.getItemModel()));
    }

    public static String capture(ItemStack stack) {
        ItemStack copy = stack.clone();
        copy.setAmount(1);
        return Base64.getEncoder().encodeToString(copy.serializeAsBytes());
    }

    public static ItemStack restore(String data) {
        if (data == null || data.isBlank()) {
            return null;
        }
        try {
            ItemStack stack = ItemStack.deserializeBytes(Base64.getDecoder().decode(data));
            if (stack == null || stack.getType().isAir()) {
                return null;
            }
            stack.setAmount(1);
            return stack;
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
