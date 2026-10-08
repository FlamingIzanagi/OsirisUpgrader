package com.osiris.upgrader.gui;

import com.osiris.upgrader.OsirisUpgraderPlugin;
import com.osiris.upgrader.item.BaseValueCatalog;
import com.osiris.upgrader.util.Permissions;
import com.osiris.upgrader.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ValueCatalogGui {
    private static final int SIZE = 54;
    private static final int PREVIOUS = 45;
    private static final int CLOSE = 49;
    private static final int NEXT = 53;
    private static final List<Integer> CONTENT = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    );

    private final OsirisUpgraderPlugin plugin;
    private final Map<java.util.UUID, Long> debounce = new HashMap<>();

    public ValueCatalogGui(OsirisUpgraderPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        long now = System.currentTimeMillis();
        Long last = debounce.get(player.getUniqueId());
        if (last != null && now - last < 300L
                && player.getOpenInventory().getTopInventory().getHolder() instanceof ValueCatalogHolder) {
            return;
        }
        debounce.put(player.getUniqueId(), now);
        open(player, 0);
    }

    public void open(Player player, int page) {
        if (!Permissions.has(player, Permissions.USE)) {
            return;
        }
        List<Listed> entries = entries();
        int pageSize = CONTENT.size();
        int pages = Math.max(1, (int) Math.ceil(entries.size() / (double) pageSize));
        int safePage = Math.max(0, Math.min(page, pages - 1));
        ValueCatalogHolder holder = new ValueCatalogHolder(safePage);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Text.component(plugin.messages().raw("gui-texts.values-title")));
        holder.attach(inventory);
        ItemStack filler = GuiItems.filler(Material.BLACK_STAINED_GLASS_PANE, 0, " ", true);
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }
        Map<String, String> pageTokens = pageTokens(safePage, pages);
        inventory.setItem(PREVIOUS, GuiItems.of(
                Material.ARROW,
                plugin.messages().format("gui-texts.values-previous.name", pageTokens),
                plugin.messages().formatList("gui-texts.values-previous.lore", pageTokens),
                false,
                1
        ));
        inventory.setItem(NEXT, GuiItems.of(
                Material.ARROW,
                plugin.messages().format("gui-texts.values-next.name", pageTokens),
                plugin.messages().formatList("gui-texts.values-next.lore", pageTokens),
                false,
                1
        ));
        inventory.setItem(CLOSE, GuiItems.of(
                Material.BARRIER,
                plugin.messages().raw("gui-texts.values-close.name"),
                plugin.messages().list("gui-texts.values-close.lore"),
                false,
                1
        ));
        if (entries.isEmpty()) {
            inventory.setItem(CONTENT.getFirst(), GuiItems.of(
                    Material.BARRIER,
                    plugin.messages().raw("gui-texts.values-empty.name"),
                    plugin.messages().list("gui-texts.values-empty.lore"),
                    false,
                    1
            ));
        } else {
            int from = safePage * pageSize;
            for (int index = 0; index < pageSize && from + index < entries.size(); index++) {
                inventory.setItem(CONTENT.get(index), icon(entries.get(from + index)));
            }
        }
        player.openInventory(inventory);
    }

    public void click(Player player, ValueCatalogHolder holder, int raw) {
        if (raw == CLOSE) {
            player.closeInventory();
            return;
        }
        if (raw == NEXT) {
            open(player, holder.page() + 1);
            return;
        }
        if (raw == PREVIOUS) {
            open(player, holder.page() - 1);
        }
    }

    private ItemStack icon(Listed listed) {
        ItemStack icon = listed.item().clone();
        icon.setAmount(1);
        List<Component> lore = new ArrayList<>();
        if (icon.lore() != null) {
            lore.addAll(icon.lore());
        }
        Map<String, String> tokens = Map.of("value", Text.compact(listed.value()));
        for (String line : plugin.messages().formatList("gui-texts.values-entry", tokens)) {
            lore.add(Text.itemText(line));
        }
        icon.editMeta(meta -> meta.lore(lore));
        return icon;
    }

    private List<Listed> entries() {
        List<Listed> vanilla = new ArrayList<>();
        plugin.itemValues().vanillaEntries().forEach((material, value) -> {
            if (material != null && material.isItem() && !material.isAir() && value > 0.0d) {
                vanilla.add(new Listed(ItemStack.of(material), value));
            }
        });
        List<Listed> all = new ArrayList<>(vanilla);
        for (BaseValueCatalog.Entry entry : plugin.baseValues().all()) {
            if (entry.item() == null || entry.item().getType().isAir()) {
                continue;
            }
            all.add(new Listed(entry.item(), entry.value()));
        }
        all.sort(Comparator.comparingDouble(Listed::value).reversed());
        return all;
    }

    private static Map<String, String> pageTokens(int page, int pages) {
        Map<String, String> tokens = new HashMap<>();
        tokens.put("page", Integer.toString(page + 1));
        tokens.put("pages", Integer.toString(pages));
        return tokens;
    }

    private record Listed(ItemStack item, double value) {
    }
}
