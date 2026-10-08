package com.osiris.upgrader.listener;

import com.osiris.upgrader.OsirisUpgraderPlugin;
import com.osiris.upgrader.gui.RewardBrowserHolder;
import com.osiris.upgrader.gui.UpgraderHolder;
import com.osiris.upgrader.gui.ValueCatalogHolder;
import com.osiris.upgrader.item.ItemMover;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import java.util.Map;

public final class GuiListener implements Listener {
    private final OsirisUpgraderPlugin plugin;

    public GuiListener(OsirisUpgraderPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof ValueCatalogHolder catalog) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                plugin.valuesGui().click(player, catalog, event.getRawSlot());
            }
            return;
        }
        if (top.getHolder() instanceof RewardBrowserHolder browser) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                handleBrowser(player, browser, event.getRawSlot());
            }
            return;
        }
        if (!(top.getHolder() instanceof UpgraderHolder holder) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (holder.isSpinning() || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }
        int raw = event.getRawSlot();
        if (raw < 0) {
            event.setCancelled(false);
            return;
        }
        boolean inTop = raw < top.getSize();
        if (inTop && raw == holder.layout().origin) {
            event.setCancelled(false);
            scheduleRefresh(player, holder);
            return;
        }
        if (!inTop) {
            event.setCancelled(false);
            if (event.isShiftClick()) {
                scheduleRefresh(player, holder);
            }
            return;
        }
        event.setCancelled(true);
        var layout = holder.layout();
        if (raw == layout.plus1) {
            adjust(player, holder, event.isRightClick() ? -1 : 1);
        } else if (raw == layout.plus15) {
            adjust(player, holder, event.isRightClick() ? -15 : 15);
        } else if (raw == layout.plus32) {
            adjust(player, holder, event.isRightClick() ? -32 : 32);
        } else if (raw == layout.x2) {
            multiplier(player, holder, 2);
        } else if (raw == layout.x4) {
            multiplier(player, holder, 4);
        } else if (raw == layout.x8) {
            multiplier(player, holder, 8);
        } else if (raw == layout.target) {
            plugin.sounds().play(player, "click", 1.1f);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && !holder.abandoned() && !holder.isSpinning()) {
                    plugin.browser().open(player, holder, 0);
                }
            });
        } else if (raw == layout.spin) {
            plugin.sounds().play(player, "click", 1.4f);
            plugin.animator().start(player, holder);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof UpgraderHolder holder)) {
            if (event.getView().getTopInventory().getHolder() instanceof RewardBrowserHolder
                    || event.getView().getTopInventory().getHolder() instanceof ValueCatalogHolder) {
                event.setCancelled(true);
            }
            return;
        }
        if (holder.isSpinning()) {
            event.setCancelled(true);
            return;
        }
        int size = event.getView().getTopInventory().getSize();
        for (int raw : event.getRawSlots()) {
            if (raw < size && raw != holder.layout().origin) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.getWhoClicked() instanceof Player player) {
            scheduleRefresh(player, holder);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof UpgraderHolder holder) {
            if (holder.isSwitching() || holder.isSpinning() || holder.abandoned()) {
                return;
            }
            holder.giveBack(player);
            plugin.sessions().release(holder);
            return;
        }
        if (event.getInventory().getHolder() instanceof RewardBrowserHolder browser) {
            if (browser.returning() || browser.session().abandoned() || plugin.isShuttingDown() || plugin.isReloading()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (plugin.isShuttingDown() || plugin.isReloading() || browser.session().abandoned()) {
                    return;
                }
                Player online = Bukkit.getPlayer(browser.playerId());
                if (online == null || !online.isOnline()) {
                    return;
                }
                if (online.getOpenInventory().getTopInventory().getHolder() instanceof RewardBrowserHolder) {
                    return;
                }
                plugin.upgraderGui().reopen(online, browser.session());
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof UpgraderHolder holder) {
            boolean spinning = holder.isSpinning();
            holder.abandon(player);
            if (!spinning) {
                plugin.sessions().release(holder);
            }
        } else if (player.getOpenInventory().getTopInventory().getHolder() instanceof RewardBrowserHolder browser) {
            UpgraderHolder session = browser.session();
            boolean spinning = session.isSpinning();
            browser.setReturning(true);
            session.abandon(player);
            if (!spinning) {
                plugin.sessions().release(session);
            }
        }
        plugin.pending().deliver(player, plugin.animator()::isActive);
    }

    private void handleBrowser(Player player, RewardBrowserHolder browser, int raw) {
        var layout = plugin.configs().menu().browser;
        plugin.sounds().play(player, "click", 1.0f);
        if (raw == layout.back()) {
            plugin.browser().returnToWheel(player, browser);
        } else if (raw == layout.next()) {
            plugin.browser().open(player, browser.session(), browser.page() + 1);
        } else if (raw == layout.previous()) {
            plugin.browser().open(player, browser.session(), browser.page() - 1);
        } else if (layout.content().contains(raw)) {
            plugin.browser().select(player, browser, raw);
        }
    }

    private void adjust(Player player, UpgraderHolder holder, int delta) {
        if (plugin.rewards().isLimited(holder.target())) {
            if (delta > 0) {
                plugin.messages().send(player, "limited-one");
            }
            holder.setTargetAmount(1);
            holder.setMultiplier(1);
            plugin.sounds().play(player, "click", 1.2f);
            plugin.upgraderGui().paint(holder);
            return;
        }
        int winLimit = plugin.rewards().prizeLimit(holder.target());
        int cap = winCap(holder, winLimit);
        if (holder.addTargetAmount(delta, cap)) {
            if (winLimit > 0 && cap < 64) {
                plugin.messages().send(player, "win-limit", limitPlaceholders(holder, winLimit, holder.multiplier()));
            } else {
                plugin.messages().send(player, "amount-capped");
            }
        }
        plugin.sounds().play(player, "click", 1.2f);
        plugin.upgraderGui().paint(holder);
    }

    private void multiplier(Player player, UpgraderHolder holder, int value) {
        if (plugin.rewards().isLimited(holder.target())) {
            plugin.messages().send(player, "limited-no-multi");
            holder.setTargetAmount(1);
            holder.setMultiplier(1);
            plugin.sounds().play(player, "click", 1.0f);
            plugin.upgraderGui().paint(holder);
            return;
        }
        int next = holder.multiplier() == value ? 1 : value;
        int winLimit = plugin.rewards().prizeLimit(holder.target());
        int win = Math.max(1, holder.targetAmount()) * Math.max(1, next);
        if (winLimit > 0 && win > winLimit) {
            plugin.messages().send(player, "win-limit", limitPlaceholders(holder, winLimit, value));
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.closeInventory();
                }
            });
            return;
        }
        holder.setMultiplier(next);
        plugin.sounds().play(player, "click", 1.3f);
        plugin.upgraderGui().paint(holder);
    }

    private int winCap(UpgraderHolder holder, int winLimit) {
        if (winLimit <= 0) {
            return 64;
        }
        int perClick = Math.max(1, winLimit / Math.max(1, holder.multiplier()));
        return Math.min(64, perClick);
    }

    private Map<String, String> limitPlaceholders(UpgraderHolder holder, int limit, int multiplier) {
        String name = holder.target() == null ? "" : plugin.rewards().nameOf(holder.target());
        return Map.of(
                "limit", Integer.toString(limit),
                "reward_name", name,
                "amount", Integer.toString(holder.targetAmount()),
                "multiplier", "x" + multiplier,
                "win_amount", Integer.toString(Math.max(1, holder.targetAmount()) * Math.max(1, multiplier))
        );
    }

    private void scheduleRefresh(Player player, UpgraderHolder holder) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (holder.isSpinning() || holder.abandoned() || !holder.isOpenFor(player)) {
                return;
            }
            var origin = holder.origin();
            if (origin != null && plugin.specialItems().isSpecial(origin)) {
                holder.inventory().setItem(holder.layout().origin, null);
                ItemMover.giveOrDrop(player, origin);
                plugin.messages().send(player, "special-item-blocked");
            }
            plugin.upgraderGui().paint(holder);
        });
    }
}
