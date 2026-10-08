package com.osiris.upgrader.gui;

import com.osiris.upgrader.OsirisUpgraderPlugin;
import com.osiris.upgrader.item.BaseValueCatalog;
import com.osiris.upgrader.item.ItemSnapshots;
import com.osiris.upgrader.util.Permissions;
import com.osiris.upgrader.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BaseEditor {
    public static final int LIST_SIZE = 54;
    public static final int PREVIEW_SLOT = 13;
    public static final int VALUE_SLOT = 11;
    public static final int DELETE_SLOT = 22;

    private final OsirisUpgraderPlugin plugin;
    private final Map<UUID, String> drafts = new ConcurrentHashMap<>();

    public BaseEditor(OsirisUpgraderPlugin plugin) {
        this.plugin = plugin;
    }

    public void openList(Player player) {
        if (!Permissions.has(player, Permissions.BASE)) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        BaseListHolder holder = new BaseListHolder();
        Inventory inventory = Bukkit.createInventory(holder, LIST_SIZE, Text.component("&8Valor base"));
        holder.attach(inventory);
        paintList(inventory);
        player.openInventory(inventory);
    }

    public void paintList(Inventory inventory) {
        inventory.clear();
        List<BaseValueCatalog.Entry> entries = plugin.baseValues().all();
        int limit = Math.min(entries.size(), inventory.getSize());
        for (int slot = 0; slot < limit; slot++) {
            inventory.setItem(slot, icon(entries.get(slot)));
        }
    }

    public void openEdit(Player player, BaseValueCatalog.Entry entry) {
        if (player == null || entry == null || !player.isOnline()) {
            return;
        }
        if (!Permissions.has(player, Permissions.BASE)) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        BaseEditHolder holder = new BaseEditHolder(entry.key());
        Inventory inventory = Bukkit.createInventory(holder, 27, Text.component("&8Editar valor base"));
        holder.attach(inventory);
        ItemStack filler = GuiItems.filler(Material.BLACK_STAINED_GLASS_PANE, 0, " ", true);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        inventory.setItem(PREVIEW_SLOT, preview(entry));
        inventory.setItem(VALUE_SLOT, GuiItems.of(
                Material.GOLD_INGOT,
                "&eValor base",
                List.of(
                        "&7Actual: &f" + Text.compact(entry.value()),
                        "&7Sirve para apostar este ítem.",
                        "&eClic izquierdo &7y escribe el nuevo valor."
                ),
                false,
                1
        ));
        inventory.setItem(DELETE_SLOT, GuiItems.of(
                Material.BARRIER,
                "&cQuitar valor base",
                List.of("&7Clic izquierdo para quitar este ítem."),
                false,
                1
        ));
        player.openInventory(inventory);
    }

    public boolean accept(Player player, ItemStack source) {
        if (player == null || !Permissions.has(player, Permissions.BASE)) {
            if (player != null) {
                plugin.messages().send(player, "no-permission");
            }
            return false;
        }
        if (source == null || source.getType().isAir() || !source.getType().isItem() || plugin.specialItems().isSpecial(source)) {
            plugin.messages().send(player, "base-denied");
            return false;
        }
        boolean nexo = plugin.nexo().idFromItem(source) != null;
        if (!nexo && !ItemSnapshots.isCustom(source)) {
            plugin.messages().send(player, "base-vanilla");
            return false;
        }
        if (plugin.baseValues().all().size() >= LIST_SIZE) {
            plugin.messages().send(player, "base-full");
            return false;
        }
        if (plugin.baseValues().matching(source) != null) {
            plugin.messages().send(player, "base-exists");
            return false;
        }
        double initial = nexo
                ? plugin.configs().items().getDouble("defaults.nexo", 25.0d)
                : plugin.configs().items().getDouble("defaults.custom", 25.0d);
        BaseValueCatalog.Entry added = plugin.baseValues().add(source, initial);
        if (added == null) {
            plugin.messages().send(player, "base-denied");
            return false;
        }
        plugin.messages().send(player, "base-added", Map.of("item", label(added), "value", Text.compact(added.value())));
        return true;
    }

    public void beginValue(Player player, String key) {
        BaseValueCatalog.Entry entry = plugin.baseValues().byKey(key);
        if (entry == null) {
            openList(player);
            return;
        }
        drafts.put(player.getUniqueId(), key);
        player.closeInventory();
        plugin.messages().send(player, "base-type-value", Map.of("item", label(entry)));
    }

    public void delete(Player player, String key) {
        BaseValueCatalog.Entry entry = plugin.baseValues().byKey(key);
        String name = entry == null ? key : label(entry);
        plugin.baseValues().remove(key);
        drafts.remove(player.getUniqueId());
        plugin.messages().send(player, "base-removed", Map.of("item", name));
        openList(player);
    }

    public boolean hasDraft(UUID playerId) {
        return drafts.containsKey(playerId);
    }

    public void clearDrafts() {
        drafts.clear();
    }

    public void forget(UUID playerId) {
        drafts.remove(playerId);
    }

    public void receive(Player player, String raw) {
        String key = drafts.get(player.getUniqueId());
        if (key == null) {
            return;
        }
        if (!Permissions.has(player, Permissions.BASE)) {
            drafts.remove(player.getUniqueId());
            plugin.messages().send(player, "no-permission");
            return;
        }
        String text = raw == null ? "" : raw.trim();
        if (text.equalsIgnoreCase("cancelar") || text.equalsIgnoreCase("cancel")) {
            drafts.remove(player.getUniqueId());
            plugin.messages().send(player, "base-cancelled");
            reopen(player, key);
            return;
        }
        BaseValueCatalog.Entry entry = plugin.baseValues().byKey(key);
        if (entry == null) {
            drafts.remove(player.getUniqueId());
            return;
        }
        try {
            double value = Double.parseDouble(text.replace(',', '.'));
            if (!Double.isFinite(value) || value < 0.0d) {
                throw new NumberFormatException();
            }
            plugin.baseValues().setValue(key, value);
            drafts.remove(player.getUniqueId());
            plugin.messages().send(player, "base-value-set", Map.of("item", label(entry), "value", Text.compact(value)));
        } catch (NumberFormatException ex) {
            plugin.messages().send(player, "base-invalid-number");
            return;
        }
        reopen(player, key);
    }

    private void reopen(Player player, String key) {
        if (!player.isOnline()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> openEdit(player, plugin.baseValues().byKey(key)));
    }

    private ItemStack icon(BaseValueCatalog.Entry entry) {
        ItemStack stack = entry.item().clone();
        stack.editMeta(meta -> meta.lore(List.of(
                Text.itemText("&7Valor base: &f" + Text.compact(entry.value())),
                Text.itemText("&eClic derecho para editar")
        )));
        return stack;
    }

    private ItemStack preview(BaseValueCatalog.Entry entry) {
        ItemStack stack = entry.item().clone();
        stack.editMeta(meta -> {
            List<Component> lore = new ArrayList<>();
            if (meta.lore() != null) {
                lore.addAll(meta.lore());
            }
            lore.add(Text.itemText("&7Valor base: &f" + Text.compact(entry.value())));
            lore.add(Text.itemText("&7Se usa al apostar este ítem."));
            meta.lore(lore);
        });
        return stack;
    }

    private String label(BaseValueCatalog.Entry entry) {
        String name = Text.plainName(entry.item());
        return name == null || name.isBlank() ? entry.key() : name;
    }
}
