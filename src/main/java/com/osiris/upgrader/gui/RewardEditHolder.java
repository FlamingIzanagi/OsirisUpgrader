package com.osiris.upgrader.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class RewardEditHolder implements InventoryHolder {
    private final String key;
    private Inventory inventory;

    public RewardEditHolder(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
