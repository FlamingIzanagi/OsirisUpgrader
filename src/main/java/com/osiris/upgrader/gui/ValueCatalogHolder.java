package com.osiris.upgrader.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ValueCatalogHolder implements InventoryHolder {
    private final int page;
    private Inventory inventory;

    public ValueCatalogHolder(int page) {
        this.page = page;
    }

    public int page() {
        return page;
    }

    public void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
