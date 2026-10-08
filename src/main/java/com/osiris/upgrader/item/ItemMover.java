package com.osiris.upgrader.item;

import com.osiris.upgrader.hook.NexoBridge;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;

public final class ItemMover {
    private final NexoBridge nexo;

    public ItemMover(NexoBridge nexo) {
        this.nexo = nexo;
    }

    public int count(Player player, Inventory gui, int originSlot, ItemStack sample) {
        int total = 0;
        ItemStack slot = gui.getItem(originSlot);
        if (similar(slot, sample)) {
            total += slot.getAmount();
        }
        PlayerInventory inventory = player.getInventory();
        for (int slotIndex : storageSlots()) {
            ItemStack stack = inventory.getItem(slotIndex);
            if (similar(stack, sample)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    public void take(Player player, Inventory gui, int originSlot, ItemStack sample, int amount) {
        int remaining = takeFrom(gui, originSlot, sample, amount);
        PlayerInventory inventory = player.getInventory();
        for (int slotIndex : storageSlots()) {
            if (remaining <= 0) {
                break;
            }
            remaining = takeFrom(inventory, slotIndex, sample, remaining);
        }
    }

    public boolean similar(ItemStack left, ItemStack right) {
        if (left == null || right == null || left.getType().isAir() || right.getType().isAir()) {
            return false;
        }
        String leftId = nexo.idFromItem(left);
        String rightId = nexo.idFromItem(right);
        if (leftId != null || rightId != null) {
            return leftId != null && leftId.equals(rightId);
        }
        return left.isSimilar(right);
    }

    public static boolean giveOrDrop(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.getType().isAir()) {
            return false;
        }
        int amount = stack.getAmount();
        int max = Math.max(1, stack.getMaxStackSize());
        boolean dropped = false;
        int left = amount;
        while (left > 0) {
            ItemStack part = stack.clone();
            int size = Math.min(max, left);
            part.setAmount(size);
            left -= size;
            HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(part);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                dropped = true;
            }
        }
        return dropped;
    }

    private int takeFrom(Inventory inventory, int slot, ItemStack sample, int amount) {
        ItemStack stack = inventory.getItem(slot);
        if (!similar(stack, sample) || amount <= 0) {
            return amount;
        }
        int take = Math.min(stack.getAmount(), amount);
        int next = stack.getAmount() - take;
        if (next <= 0) {
            inventory.setItem(slot, null);
        } else {
            stack.setAmount(next);
        }
        return amount - take;
    }

    private static int[] storageSlots() {
        int[] slots = new int[37];
        for (int i = 0; i < 36; i++) {
            slots[i] = i;
        }
        slots[36] = 40;
        return slots;
    }
}
