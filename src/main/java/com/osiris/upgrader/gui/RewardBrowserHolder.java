package com.osiris.upgrader.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class RewardBrowserHolder implements InventoryHolder {
    private final UUID playerId;
    private final UpgraderHolder session;
    private final int page;
    private Inventory inventory;
    private boolean returning;

    public RewardBrowserHolder(UpgraderHolder session, int page) {
        this.playerId = session.playerId();
        this.session = session;
        this.page = page;
    }

    public void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public UUID playerId() {
        return playerId;
    }

    public UpgraderHolder session() {
        return session;
    }

    public int page() {
        return page;
    }

    public boolean returning() {
        return returning;
    }

    public void setReturning(boolean returning) {
        this.returning = returning;
    }
}
