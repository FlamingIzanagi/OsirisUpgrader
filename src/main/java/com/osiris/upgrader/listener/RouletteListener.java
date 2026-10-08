package com.osiris.upgrader.listener;

import com.osiris.upgrader.OsirisUpgraderPlugin;
import com.osiris.upgrader.roulette.Roulette;
import com.osiris.upgrader.util.Permissions;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class RouletteListener implements Listener {
    private final OsirisUpgraderPlugin plugin;

    public RouletteListener(OsirisUpgraderPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Roulette roulette = plugin.roulettes().findByEntity(event.getRightClicked());
        if (roulette == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!Permissions.has(player, Permissions.USE)) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        plugin.upgraderGui().open(player, roulette.id());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent event) {
        Roulette roulette = plugin.roulettes().findByEntity(event.getEntity());
        if (roulette == null) {
            return;
        }
        event.setCancelled(true);
        if (!(event instanceof EntityDamageByEntityEvent damage) || !(damage.getDamager() instanceof Player player)) {
            return;
        }
        if (!player.isSneaking()) {
            plugin.valuesGui().open(player);
            return;
        }
        plugin.roulettes().breakRoulette(player, event.getEntity());
    }

    @EventHandler(ignoreCancelled = true)
    public void onManipulate(PlayerArmorStandManipulateEvent event) {
        if (plugin.roulettes().findByEntity(event.getRightClicked()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> plugin.bedrockView().refresh(event.getPlayer()), 10L);
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        plugin.bedrockView().refresh(event.getPlayer());
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        var world = event.getWorld();
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (world.isChunkLoaded(chunkX, chunkZ)) {
                plugin.roulettes().ensureChunk(world, chunkX, chunkZ);
            }
        });
    }
}
