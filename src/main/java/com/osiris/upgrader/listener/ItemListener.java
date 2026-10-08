package com.osiris.upgrader.listener;

import com.osiris.upgrader.OsirisUpgraderPlugin;
import com.osiris.upgrader.util.Permissions;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class ItemListener implements Listener {
    private final OsirisUpgraderPlugin plugin;

    public ItemListener(OsirisUpgraderPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        var item = event.getItemInHand();
        if (plugin.specialItems().isPortable(item)) {
            event.setCancelled(true);
            return;
        }
        if (plugin.specialItems().isSpawner(item)) {
            event.setCancelled(true);
            plugin.roulettes().trySpawn(event.getPlayer(), event.getBlock().getLocation(), event.getHand());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        var action = event.getAction();
        if (action != org.bukkit.event.block.Action.RIGHT_CLICK_AIR && action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        var item = event.getItem();
        if (item == null) {
            return;
        }
        Player player = event.getPlayer();
        if (plugin.specialItems().isPortable(item)) {
            if (lookingAtRoulette(player)) {
                return;
            }
            event.setCancelled(true);
            if (event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            if (!Permissions.has(player, Permissions.USE)) {
                plugin.messages().send(player, "no-permission");
                return;
            }
            plugin.upgraderGui().open(player, null);
            return;
        }
        if (plugin.specialItems().isSpawner(item) && !item.getType().isBlock()
                && action == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null
                && event.getHand() == EquipmentSlot.HAND) {
            event.setCancelled(true);
            var face = event.getBlockFace();
            plugin.roulettes().trySpawn(player, event.getClickedBlock().getRelative(face).getLocation(), event.getHand());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (plugin.specialItems().isSpecial(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> plugin.pending().deliver(event.getPlayer(), plugin.animator()::isActive), 20L);
    }

    private boolean lookingAtRoulette(Player player) {
        var hit = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                5.0d,
                0.4d,
                entity -> plugin.roulettes().findByEntity(entity) != null
        );
        Entity entity = hit == null ? null : hit.getHitEntity();
        return entity != null;
    }
}
