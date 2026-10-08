package com.osiris.upgrader.listener;

import com.osiris.upgrader.OsirisUpgraderPlugin;
import com.osiris.upgrader.gui.RewardEditHolder;
import com.osiris.upgrader.gui.RewardEditor;
import com.osiris.upgrader.gui.RewardListHolder;
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

public final class RewardEditorListener implements Listener {
    private final OsirisUpgraderPlugin plugin;
    private final RewardEditor editor;

    public RewardEditorListener(OsirisUpgraderPlugin plugin, RewardEditor editor) {
        this.plugin = plugin;
        this.editor = editor;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof RewardListHolder) {
            boolean bottom = event.getClickedInventory() != null && event.getClickedInventory().equals(player.getInventory());
            boolean shift = event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT;
            if (bottom && !shift) {
                return;
            }
            event.setCancelled(true);
            handleList(event, player, top);
            return;
        }
        if (top.getHolder() instanceof RewardEditHolder holder) {
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
        if (event.getView().getTopInventory().getHolder() instanceof RewardListHolder
                || event.getView().getTopInventory().getHolder() instanceof RewardEditHolder) {
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
        boolean topClick = event.getClickedInventory().equals(top);
        if (topClick) {
            ItemStack shown = event.getCurrentItem();
            boolean filled = shown != null && !shown.getType().isAir();
            if (filled && event.getClick() == ClickType.RIGHT) {
                int slot = event.getSlot();
                var rewards = plugin.rewards().all();
                if (slot >= 0 && slot < rewards.size()) {
                    editor.openEdit(player, rewards.get(slot));
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

    private void handleEdit(InventoryClickEvent event, Player player, RewardEditHolder holder) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }
        if (event.getClick() != ClickType.LEFT) {
            return;
        }
        int slot = event.getSlot();
        if (slot == RewardEditor.VALUE_SLOT) {
            editor.beginValue(player, holder.key());
        } else if (slot == RewardEditor.LIMIT_SLOT) {
            editor.beginLimit(player, holder.key());
        } else if (slot == RewardEditor.STOCK_SLOT) {
            editor.beginStock(player, holder.key());
        } else if (slot == RewardEditor.GLOW_SLOT) {
            editor.toggleGlow(player, holder.key());
        } else if (slot == RewardEditor.DELETE_SLOT) {
            editor.delete(player, holder.key());
        }
    }
}
