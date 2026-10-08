package com.osiris.upgrader.item;

import org.bukkit.Material;

public record RewardEntry(
        String key,
        boolean nexo,
        String id,
        Material material,
        String displayName,
        int customModelData,
        String itemData
) {
    public boolean snapshot() {
        return itemData != null && !itemData.isBlank();
    }
}
