package com.osiris.upgrader.gui;

import com.osiris.upgrader.config.MenuLayout;
import com.osiris.upgrader.item.ItemMover;
import com.osiris.upgrader.item.RewardEntry;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class UpgraderHolder implements InventoryHolder {
    private final UUID playerId;
    private final UUID rouletteId;
    private Inventory inventory;
    private MenuLayout layout;
    private RewardEntry target;
    private int targetAmount = 1;
    private int multiplier = 1;
    private boolean spinning;
    private boolean switching;
    private boolean returned;
    private boolean abandoned;
    private ItemStack originSnapshot;

    public UpgraderHolder(UUID playerId, @Nullable UUID rouletteId) {
        this.playerId = playerId;
        this.rouletteId = rouletteId;
    }

    public void attach(Inventory inventory, MenuLayout layout) {
        this.inventory = inventory;
        this.layout = layout;
        this.returned = false;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public UUID playerId() {
        return playerId;
    }

    public UUID rouletteId() {
        return rouletteId;
    }

    public Inventory inventory() {
        return inventory;
    }

    public MenuLayout layout() {
        return layout;
    }

    public RewardEntry target() {
        return target;
    }

    public void setTarget(RewardEntry target) {
        this.target = target;
    }

    public int targetAmount() {
        return targetAmount;
    }

    public int multiplier() {
        return multiplier;
    }

    public void setMultiplier(int multiplier) {
        this.multiplier = multiplier;
    }

    public boolean isSpinning() {
        return spinning;
    }

    public void setSpinning(boolean spinning) {
        this.spinning = spinning;
    }

    public boolean isSwitching() {
        return switching;
    }

    public void setSwitching(boolean switching) {
        this.switching = switching;
    }

    public ItemStack originSnapshot() {
        return originSnapshot;
    }

    public void setOriginSnapshot(ItemStack originSnapshot) {
        this.originSnapshot = originSnapshot == null ? null : originSnapshot.clone();
    }

    public ItemStack origin() {
        if (inventory == null || layout == null) {
            return null;
        }
        ItemStack stack = inventory.getItem(layout.origin);
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        return stack;
    }

    public boolean isOpenFor(org.bukkit.entity.Player player) {
        return inventory != null && inventory.getViewers().contains(player);
    }

    public void setTargetAmount(int targetAmount) {
        this.targetAmount = Math.max(1, Math.min(64, targetAmount));
    }

    public boolean addTargetAmount(int delta, int cap) {
        int limit = cap > 0 ? Math.min(64, cap) : 64;
        int unclamped = targetAmount + delta;
        targetAmount = Math.max(1, Math.min(limit, unclamped));
        return delta > 0 && unclamped > limit;
    }

    public boolean abandoned() {
        return abandoned;
    }

    public void abandon(org.bukkit.entity.Player player) {
        abandoned = true;
        switching = false;
        giveBack(player);
    }

    public void giveBack(org.bukkit.entity.Player player) {
        if (spinning || returned || player == null) {
            return;
        }
        returned = true;
        ItemStack stack = null;
        if (inventory != null && layout != null) {
            stack = inventory.getItem(layout.origin);
            inventory.setItem(layout.origin, null);
        }
        if ((stack == null || stack.getType().isAir()) && originSnapshot != null) {
            stack = originSnapshot;
        }
        originSnapshot = null;
        ItemMover.giveOrDrop(player, stack);
    }
}
