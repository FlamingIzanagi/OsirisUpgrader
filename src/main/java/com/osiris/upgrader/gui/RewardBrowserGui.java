package com.osiris.upgrader.gui;

import com.osiris.upgrader.OsirisUpgraderPlugin;
import com.osiris.upgrader.config.MenuLayout;
import com.osiris.upgrader.item.RewardEntry;
import com.osiris.upgrader.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RewardBrowserGui {
    private final OsirisUpgraderPlugin plugin;

    public RewardBrowserGui(OsirisUpgraderPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, UpgraderHolder session, int page) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof RewardBrowserHolder current) {
            current.setReturning(true);
        } else {
            ItemStack origin = session.origin();
            session.setOriginSnapshot(origin == null ? null : origin.clone());
            if (origin != null && session.inventory() != null && session.layout() != null) {
                session.inventory().setItem(session.layout().origin, null);
            }
            session.setSwitching(true);
        }
        MenuLayout.Browser browser = plugin.configs().menu().browser;
        List<RewardEntry> entries = plugin.rewards().viewable();
        int pageSize = Math.max(1, browser.content().size());
        int pages = Math.max(1, (int) Math.ceil(entries.size() / (double) pageSize));
        int safePage = Math.max(0, Math.min(page, pages - 1));
        RewardBrowserHolder holder = new RewardBrowserHolder(session, safePage);
        String title = browser.title() == null || browser.title().isBlank()
                ? plugin.messages().raw("gui-texts.browser-title")
                : browser.title();
        Inventory inventory = Bukkit.createInventory(holder, browser.size(), Text.component(title));
        holder.attach(inventory);
        for (int slot = 0; slot < browser.size(); slot++) {
            inventory.setItem(slot, GuiItems.filler(browser.filler(), 0, " ", true));
        }
        Map<String, String> pageTokens = pageTokens(safePage, pages);
        inventory.setItem(browser.info(), GuiItems.of(
                browser.infoIcon(),
                plugin.messages().format("gui-texts.browser-info.name", pageTokens),
                plugin.messages().formatList("gui-texts.browser-info.lore", pageTokens),
                false,
                1
        ));
        inventory.setItem(browser.previous(), GuiItems.of(
                browser.previousIcon(),
                plugin.messages().format("gui-texts.browser-previous.name", pageTokens),
                plugin.messages().formatList("gui-texts.browser-previous.lore", pageTokens),
                false,
                1
        ));
        inventory.setItem(browser.next(), GuiItems.of(
                browser.nextIcon(),
                plugin.messages().format("gui-texts.browser-next.name", pageTokens),
                plugin.messages().formatList("gui-texts.browser-next.lore", pageTokens),
                false,
                1
        ));
        inventory.setItem(browser.back(), GuiItems.of(
                browser.backIcon(),
                plugin.messages().raw("gui-texts.browser-back.name"),
                List.of(),
                false,
                1
        ));
        if (entries.isEmpty()) {
            inventory.setItem(browser.content().getFirst(), GuiItems.of(
                    browser.emptyIcon(),
                    plugin.messages().raw("gui-texts.browser-empty.name"),
                    plugin.messages().list("gui-texts.browser-empty.lore"),
                    false,
                    1
            ));
        } else {
            int from = safePage * pageSize;
            for (int index = 0; index < pageSize && from + index < entries.size(); index++) {
                RewardEntry entry = entries.get(from + index);
                try {
                    inventory.setItem(browser.content().get(index), icon(entry));
                } catch (RuntimeException ex) {
                    plugin.getLogger().log(java.util.logging.Level.WARNING, "No se pudo mostrar el premio " + entry.key(), ex);
                }
            }
        }
        player.openInventory(inventory);
    }

    public void select(Player player, RewardBrowserHolder holder, int rawSlot) {
        MenuLayout.Browser browser = plugin.configs().menu().browser;
        int index = browser.content().indexOf(rawSlot);
        if (index < 0) {
            return;
        }
        List<RewardEntry> entries = plugin.rewards().viewable();
        int pageSize = Math.max(1, browser.content().size());
        int entryIndex = holder.page() * pageSize + index;
        if (entryIndex < 0 || entryIndex >= entries.size()) {
            return;
        }
        com.osiris.upgrader.item.RewardEntry picked = entries.get(entryIndex);
        if (plugin.rewards().isLimited(picked) && !plugin.rewards().available(picked)) {
            plugin.messages().send(player, "limited-gone");
            return;
        }
        holder.session().setTarget(picked);
        if (plugin.rewards().isLimited(picked)) {
            holder.session().setTargetAmount(1);
            holder.session().setMultiplier(1);
        } else {
            holder.session().addTargetAmount(0, 64);
        }
        returnToWheel(player, holder);
    }

    public void returnToWheel(Player player, RewardBrowserHolder holder) {
        holder.setReturning(true);
        plugin.upgraderGui().reopen(player, holder.session());
    }

    private ItemStack icon(RewardEntry entry) {
        ItemStack icon = plugin.rewards().createTemplate(entry);
        if (icon == null) {
            return GuiItems.of(org.bukkit.Material.BARRIER, entry.key(), List.of(), false, 1);
        }
        double unit = plugin.rewards().prizeValue(entry);
        Map<String, String> tokens = Map.of("unit_value", Text.compact(unit));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        if (icon.lore() != null) {
            lore.addAll(icon.lore());
        }
        if (plugin.rewards().isLimited(entry)) {
            for (String line : plugin.messages().lines("gui-texts.limited-badge")) {
                lore.add(Text.itemText(line));
            }
        }
        for (String line : plugin.messages().formatList("gui-texts.browser-entry", tokens)) {
            lore.add(Text.itemText(line));
        }
        int remaining = plugin.rewards().limitedRemaining(entry);
        if (remaining > 0) {
            int max = Math.max(1, icon.getMaxStackSize());
            icon.setAmount(Math.min(remaining, max));
            if (remaining > max) {
                lore.add(Text.itemText("&7Quedan: &f" + remaining));
            }
        }
        icon.editMeta(meta -> meta.lore(lore));
        return icon;
    }

    private static Map<String, String> pageTokens(int page, int pages) {
        Map<String, String> tokens = new HashMap<>();
        tokens.put("page", Integer.toString(page + 1));
        tokens.put("pages", Integer.toString(pages));
        return tokens;
    }
}
