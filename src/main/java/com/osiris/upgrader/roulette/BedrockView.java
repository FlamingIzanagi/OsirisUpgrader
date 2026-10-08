package com.osiris.upgrader.roulette;

import com.osiris.upgrader.OsirisUpgraderPlugin;
import com.osiris.upgrader.config.Keys;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

public final class BedrockView {
    private final OsirisUpgraderPlugin plugin;

    public BedrockView(OsirisUpgraderPlugin plugin) {
        this.plugin = plugin;
    }

    public void refreshOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    public void refresh(Player player) {
        if (player == null || !player.isOnline() || !plugin.bedrock().isHooked()) {
            return;
        }
        boolean bedrock = plugin.bedrock().isBedrock(player);
        for (Roulette roulette : plugin.roulettes().all()) {
            World world = roulette.world();
            Location center = roulette.center();
            if (world == null || center == null || !world.isChunkLoaded(roulette.chunkX(), roulette.chunkZ())) {
                continue;
            }
            double reach = roulette.radius() * 2.2d + 4.0d;
            for (Entity entity : world.getNearbyEntities(center, reach, reach, reach)) {
                String id = entity.getPersistentDataContainer().get(Keys.ROULETTE_ID, PersistentDataType.STRING);
                if (!roulette.id().toString().equals(id)) {
                    continue;
                }
                String role = entity.getPersistentDataContainer().get(Keys.ROLE, PersistentDataType.STRING);
                boolean layer = role != null && role.startsWith("bedrock");
                if (layer) {
                    if (bedrock) {
                        player.showEntity(plugin, entity);
                    } else {
                        player.hideEntity(plugin, entity);
                    }
                } else if (bedrock && entity instanceof Display) {
                    player.hideEntity(plugin, entity);
                }
            }
        }
    }
}
