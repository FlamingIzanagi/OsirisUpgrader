package com.osiris.upgrader.util;

import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

import java.util.List;

public final class Models {
    private Models() {
    }

    public static void apply(ItemMeta meta, int modelData) {
        if (meta == null || modelData <= 0) {
            return;
        }
        CustomModelDataComponent component = meta.getCustomModelDataComponent();
        if (component == null) {
            return;
        }
        component.setFloats(List.of((float) modelData));
        meta.setCustomModelDataComponent(component);
    }
}
