package com.osiris.upgrader.listener;

import com.osiris.upgrader.OsirisUpgraderPlugin;
import com.osiris.upgrader.gui.BaseEditHolder;
import com.osiris.upgrader.gui.BaseEditor;
import com.osiris.upgrader.gui.BaseListHolder;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class BaseEditorListener implements Listener {
    private final OsirisUpgraderPlugin plugin;
    private final BaseEditor editor;

    public BaseEditorListener(OsirisUpgraderPlugin plugin, BaseEditor editor) {
        this.plugin = plugin;
        this.editor = editor;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof BaseListHolder) {
            boolean bottom = event.getClickedInventory() != null && event.getClickedInventory().equals(player.getInventory());
            boolean shift = event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT;
            if (bottom && !shift) {
                return;
            }
            event.setCancelled(true);
            handleList(event, player, top);
            return;
        }
        if (top.getHolder() instanceof BaseEditHolder holder) {
            boolean bottom = event.getClickedInventory() != null && event.getClickedInventory().equals(player.getInventory());
            boolean shift = event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT;
            if (bottom && !shift) {
                return;
            }
            event.setCancelled(true);
            if (!bottom) {
                handleEdit(event, player, holder);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BaseListHolder
                || event.getView().getTopInventory().getHolder() instanceof BaseEditHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!editor.hasDraft(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        plugin.getServer().getScheduler().runTask(plugin, () -> editor.receive(player, plain));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        editor.forget(event.getPlayer().getUniqueId());
    }

    private void handleList(InventoryClickEvent event, Player player, Inventory top) {
        if (event.getClickedInventory() == null) {
            return;
        }
        if (event.getClickedInventory().equals(top)) {
            ItemStack shown = event.getCurrentItem();
            boolean filled = shown != null && !shown.getType().isAir();
            if (filled && event.getClick() == ClickType.RIGHT) {
                var entries = plugin.baseValues().all();
                int slot = event.getSlot();
                if (slot >= 0 && slot < entries.size()) {
                    editor.openEdit(player, entries.get(slot));
                }
                return;
            }
            if (filled || (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT)) {
                return;
            }
            placeCursor(event, player, top);
            return;
        }
        if (event.getClick() != ClickType.SHIFT_LEFT && event.getClick() != ClickType.SHIFT_RIGHT) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType().isAir()) {
            return;
        }
        ItemStack one = current.clone();
        one.setAmount(1);
        if (!editor.accept(player, one)) {
            return;
        }
        if (current.getAmount() <= 1) {
            event.getClickedInventory().setItem(event.getSlot(), null);
        } else {
            current.setAmount(current.getAmount() - 1);
        }
        editor.paintList(top);
    }

    private void placeCursor(InventoryClickEvent event, Player player, Inventory top) {
        ItemStack cursor = event.getCursor();
        if (cursor == null || cursor.getType().isAir()) {
            return;
        }
        ItemStack one = cursor.clone();
        one.setAmount(1);
        if (!editor.accept(player, one)) {
            return;
        }
        if (cursor.getAmount() <= 1) {
            event.getView().setCursor(null);
        } else {
            cursor.setAmount(cursor.getAmount() - 1);
            event.getView().setCursor(cursor);
        }
        editor.paintList(top);
    }

    private void handleEdit(InventoryClickEvent event, Player player, BaseEditHolder holder) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }
        if (event.getClick() != ClickType.LEFT) {
            return;
        }
        if (event.getSlot() == BaseEditor.VALUE_SLOT) {
            editor.beginValue(player, holder.key());
        } else if (event.getSlot() == BaseEditor.DELETE_SLOT) {
            editor.delete(player, holder.key());
        }
    }
}
