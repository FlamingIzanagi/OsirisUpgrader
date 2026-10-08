package com.osiris.upgrader.roulette;

import com.osiris.upgrader.OsirisUpgraderPlugin;
import com.osiris.upgrader.config.Keys;
import com.osiris.upgrader.item.RewardEntry;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class RouletteManager {
    private final OsirisUpgraderPlugin plugin;
    private final Map<UUID, Roulette> roulettes = new LinkedHashMap<>();
    private final File file;
    private boolean loaded;
    private BukkitTask showcaseTask;
    private int showcaseIndex;
    private ItemStack showcaseIcon;
    private Component showcaseLabel;

    public RouletteManager(OsirisUpgraderPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "ruletas.yml");
    }

    public Collection<Roulette> all() {
        return roulettes.values();
    }

    public Roulette get(UUID id) {
        return id == null ? null : roulettes.get(id);
    }

    public Roulette findByEntity(Entity entity) {
        if (entity == null || Keys.ROULETTE_ID == null) {
            return null;
        }
        String raw = entity.getPersistentDataContainer().get(Keys.ROULETTE_ID, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return roulettes.get(UUID.fromString(raw));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public void load() {
        roulettes.clear();
        loaded = true;
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("roulettes");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            try {
                Roulette roulette = read(UUID.fromString(key), section);
                roulettes.put(roulette.id(), roulette);
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "No se pudo leer la ruleta " + key, ex);
            }
        }
        plugin.getLogger().info("Ruletas cargadas: " + roulettes.size());
    }

    public void save() {
        if (!loaded) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("roulettes");
        for (Roulette roulette : roulettes.values()) {
            ConfigurationSection section = root.createSection(roulette.id().toString());
            section.set("world", roulette.worldName());
            if (roulette.worldId() != null) {
                section.set("world-uuid", roulette.worldId().toString());
            }
            section.set("x", roulette.x());
            section.set("y", roulette.y());
            section.set("z", roulette.z());
            section.set("base-y", roulette.baseY());
            section.set("yaw", roulette.yaw());
            if (roulette.owner() != null) {
                section.set("owner", roulette.owner().toString());
            }
            section.set("segments", roulette.segments());
            section.set("radius", roulette.radius());
            section.set("frame-gap", roulette.frameGap());
            section.set("small", roulette.smallStands());
            section.set("marker", roulette.markerVisuals());
            section.set("rings", roulette.rings());
            section.set("structure-version", roulette.structureVersion());
            section.set("segment-ids", strings(roulette.segmentIds()));
            section.set("frame-ids", strings(roulette.frameIds()));
            section.set("needle-ids", strings(roulette.needleIds()));
            section.set("decor-ids", strings(roulette.decorIds()));
            if (roulette.interactionId() != null) {
                section.set("interaction-id", roulette.interactionId().toString());
            }
            if (roulette.hologramId() != null) {
                section.set("hologram-id", roulette.hologramId().toString());
            }
            if (roulette.coreId() != null) {
                section.set("core-id", roulette.coreId().toString());
            }
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo guardar ruletas.yml", ex);
        }
    }

    public boolean trySpawn(Player player, Location base, EquipmentSlot hand) {
        if (roulettes.size() >= plugin.configs().roulette().maxRoulettes()) {
            plugin.messages().send(player, "roulette-limit");
            return false;
        }
        World world = base.getWorld();
        if (world == null) {
            return false;
        }
        double x = base.getBlockX() + 0.5d;
        double z = base.getBlockZ() + 0.5d;
        double min = plugin.configs().roulette().minDistance();
        if (min > 0.0d) {
            for (Roulette other : roulettes.values()) {
                if (!other.worldName().equals(world.getName())) {
                    continue;
                }
                double dx = other.x() - x;
                double dz = other.z() - z;
                if ((dx * dx) + (dz * dz) < min * min) {
                    plugin.messages().send(player, "roulette-too-close");
                    return false;
                }
            }
        }
        Roulette roulette = plugin.factory().spawn(base, player);
        if (roulette == null) {
            return false;
        }
        roulettes.put(roulette.id(), roulette);
        save();
        refreshShowcase(roulette);
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            var stack = hand == EquipmentSlot.OFF_HAND
                    ? player.getInventory().getItemInOffHand()
                    : player.getInventory().getItemInMainHand();
            stack.setAmount(stack.getAmount() - 1);
        }
        plugin.messages().send(player, "roulette-placed");
        return true;
    }

    public boolean breakRoulette(Player player, Entity entity) {
        Roulette roulette = findByEntity(entity);
        if (roulette == null) {
            return false;
        }
        if (roulette.spinning()) {
            plugin.messages().send(player, "cannot-break-spinning");
            return true;
        }
        if (!com.osiris.upgrader.util.Permissions.has(player, com.osiris.upgrader.util.Permissions.BREAK)) {
            return true;
        }
        plugin.factory().removeEntities(roulette);
        roulettes.remove(roulette.id());
        save();
        com.osiris.upgrader.item.ItemMover.giveOrDrop(player, plugin.specialItems().spawner());
        plugin.messages().send(player, "roulette-removed");
        return true;
    }

    public void ensureChunk(World world, int chunkX, int chunkZ) {
        for (Roulette roulette : new ArrayList<>(roulettes.values())) {
            if (!sameWorld(roulette, world) || roulette.chunkX() != chunkX || roulette.chunkZ() != chunkZ) {
                continue;
            }
            boolean outdated = roulette.structureVersion() < Roulette.CURRENT_STRUCTURE;
            if (roulette.spinning()) {
                continue;
            }
            boolean missing = !anyAlive(roulette);
            boolean warped = !missing && !discIntact(roulette);
            boolean duplicated = !missing && duplicated(roulette);
            if (outdated || missing || warped || duplicated) {
                plugin.getLogger().info(outdated
                        ? "Actualizando la ruleta " + roulette.id() + " al disco de colores."
                        : missing
                        ? "Reconstruyendo ruleta " + roulette.id() + " porque sus entidades ya no están."
                        : duplicated
                        ? "Reconstruyendo ruleta " + roulette.id() + " porque había piezas duplicadas."
                        : "Reconstruyendo ruleta " + roulette.id() + " porque el disco perdió la forma.");
                plugin.factory().rebuild(roulette);
                save();
            } else {
                plugin.factory().ensureBedrockLayer(roulette);
            }
        }
    }

    public void ensureLoadedChunks() {
        for (World world : Bukkit.getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                ensureChunk(world, chunk.getX(), chunk.getZ());
            }
        }
    }

    public void refreshHolograms() {
        for (Roulette roulette : roulettes.values()) {
            plugin.factory().refreshHologram(roulette);
        }
    }

    public void startShowcase() {
        stopShowcase();
        long period = plugin.configs().showcaseIntervalTicks();
        showcaseTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::cycleShowcase, period, period);
        cycleShowcase();
    }

    public void stopShowcase() {
        if (showcaseTask != null) {
            showcaseTask.cancel();
            showcaseTask = null;
        }
    }

    public void refreshShowcase(Roulette roulette) {
        if (roulette == null || roulette.spinning() || roulette.prizeShown()) {
            return;
        }
        if (showcaseIcon == null) {
            showcaseIcon = showcaseIcon(showcaseIndex);
            showcaseLabel = showcaseLabel(showcaseIndex);
        }
        if (showcaseIcon != null) {
            plugin.factory().showShowcase(roulette, showcaseIcon, showcaseLabel);
        }
    }

    private void cycleShowcase() {
        List<RewardEntry> rewards = plugin.rewards().viewable();
        if (rewards.isEmpty()) {
            showcaseIcon = null;
            showcaseLabel = null;
            return;
        }
        if (showcaseIndex >= rewards.size()) {
            showcaseIndex = 0;
        }
        int index = showcaseIndex;
        ItemStack icon = showcaseIcon(index);
        showcaseIndex++;
        if (icon == null) {
            return;
        }
        showcaseIcon = icon;
        showcaseLabel = showcaseLabel(index);
        for (Roulette roulette : roulettes.values()) {
            if (!roulette.spinning() && !roulette.prizeShown()) {
                plugin.factory().showShowcase(roulette, icon, showcaseLabel);
            }
        }
    }

    private ItemStack showcaseIcon(int index) {
        List<RewardEntry> rewards = plugin.rewards().viewable();
        if (rewards.isEmpty()) {
            return null;
        }
        ItemStack icon = plugin.rewards().createTemplate(rewards.get(Math.floorMod(index, rewards.size())));
        if (icon != null) {
            icon.setAmount(1);
        }
        return icon;
    }

    private Component showcaseLabel(int index) {
        List<RewardEntry> rewards = plugin.rewards().viewable();
        if (rewards.isEmpty()) {
            return null;
        }
        return plugin.rewards().showcaseLabel(rewards.get(Math.floorMod(index, rewards.size())));
    }

    public void clearSpinningFlags() {
        for (Roulette roulette : roulettes.values()) {
            roulette.setSpinning(false);
        }
    }

    public int purgeLeftovers(Location origin, double radius) {
        double radiusSquared = radius * radius;
        List<Roulette> doomed = new ArrayList<>();
        for (Roulette roulette : roulettes.values()) {
            if (covers(roulette, origin, radiusSquared)) {
                doomed.add(roulette);
            }
        }
        int removed = 0;
        Set<UUID> tracked = new HashSet<>();
        for (Roulette roulette : doomed) {
            tracked.addAll(roulette.allIds());
            World world = roulette.world();
            if (world != null) {
                world.getChunkAt(roulette.chunkX(), roulette.chunkZ()).load(false);
            }
            roulette.setSpinning(false);
            roulette.bumpDisplay();
            int entities = plugin.factory().removeEntities(roulette);
            removed += Math.max(1, entities);
            roulettes.remove(roulette.id());
        }
        if (!doomed.isEmpty()) {
            save();
        }
        for (Roulette roulette : roulettes.values()) {
            tracked.addAll(roulette.allIds());
            if (origin != null && !sameWorld(roulette, origin.getWorld())) {
                continue;
            }
            World world = roulette.world();
            if (world != null) {
                world.getChunkAt(roulette.chunkX(), roulette.chunkZ()).load(false);
            }
        }
        List<World> worlds = origin == null ? Bukkit.getWorlds() : List.of(origin.getWorld());
        for (World world : worlds) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (!isLeftoverRoulette(entity, tracked)) {
                    continue;
                }
                if (origin != null && entity.getLocation().distanceSquared(origin) > radiusSquared) {
                    continue;
                }
                entity.remove();
                removed++;
            }
        }
        return removed;
    }

    private boolean covers(Roulette roulette, Location origin, double radiusSquared) {
        if (origin == null) {
            return true;
        }
        if (!sameWorld(roulette, origin.getWorld())) {
            return false;
        }
        Location center = roulette.center();
        return center != null && center.distanceSquared(origin) <= radiusSquared;
    }

    private boolean isLeftoverRoulette(Entity entity, Set<UUID> tracked) {
        if (tracked.contains(entity.getUniqueId())) {
            return false;
        }
        if (entity.getScoreboardTags().contains("osiris_roulette")) {
            return true;
        }
        return Keys.ROULETTE_ID != null
                && entity.getPersistentDataContainer().has(Keys.ROULETTE_ID, PersistentDataType.STRING);
    }

    private boolean duplicated(Roulette roulette) {
        int nearby = taggedNearby(roulette);
        int expected = roulette.allIds().size();
        return nearby > expected + 8;
    }

    private int taggedNearby(Roulette roulette) {
        World world = roulette.world();
        Location center = roulette.center();
        if (world == null || center == null || !world.isChunkLoaded(roulette.chunkX(), roulette.chunkZ())) {
            return 0;
        }
        double reach = Math.max(roulette.radius() + 3.0d, roulette.y() - roulette.baseY() + 2.0d);
        int count = 0;
        for (Entity entity : world.getNearbyEntities(center, reach, reach, reach)) {
            String id = entity.getPersistentDataContainer().get(Keys.ROULETTE_ID, PersistentDataType.STRING);
            if (!roulette.id().toString().equals(id)) {
                continue;
            }
            String role = entity.getPersistentDataContainer().get(Keys.ROLE, PersistentDataType.STRING);
            if (role != null && role.startsWith("bedrock")) {
                continue;
            }
            count++;
        }
        return count;
    }

    private boolean discIntact(Roulette roulette) {
        int seen = 0;
        for (UUID id : roulette.segmentIds()) {
            Entity entity = Bukkit.getEntity(id);
            if (!(entity instanceof BlockDisplay display)) {
                continue;
            }
            seen++;
            float depth = display.getTransformation().getScale().z();
            if (depth > 0.25f) {
                return false;
            }
        }
        return seen > 0;
    }

    private boolean anyAlive(Roulette roulette) {
        for (UUID id : roulette.allIds()) {
            if (Bukkit.getEntity(id) != null) {
                return true;
            }
        }
        World world = roulette.world();
        Location center = roulette.center();
        if (world == null || center == null || !world.isChunkLoaded(roulette.chunkX(), roulette.chunkZ())) {
            return true;
        }
        double reach = roulette.radius() + roulette.frameGap() + 3.0d;
        for (Entity entity : world.getNearbyEntities(center, reach, reach, reach)) {
            String id = entity.getPersistentDataContainer().get(Keys.ROULETTE_ID, PersistentDataType.STRING);
            if (roulette.id().toString().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private boolean sameWorld(Roulette roulette, World world) {
        if (roulette.worldId() != null && roulette.worldId().equals(world.getUID())) {
            return true;
        }
        return roulette.worldName().equals(world.getName());
    }

    private Roulette read(UUID id, ConfigurationSection section) {
        UUID worldId = null;
        String worldRaw = section.getString("world-uuid");
        if (worldRaw != null && !worldRaw.isBlank()) {
            worldId = UUID.fromString(worldRaw);
        }
        UUID owner = null;
        String ownerRaw = section.getString("owner");
        if (ownerRaw != null && !ownerRaw.isBlank()) {
            owner = UUID.fromString(ownerRaw);
        }
        Roulette roulette = new Roulette(
                id,
                owner,
                section.getString("world", "world"),
                worldId,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                section.getDouble("base-y"),
                (float) section.getDouble("yaw"),
                section.getInt("segments", 20),
                section.getDouble("radius", 1.35d),
                section.getDouble("frame-gap", 0.42d),
                section.getBoolean("small", true),
                section.getBoolean("marker", false)
        );
        roulette.segmentIds().addAll(uuids(section.getStringList("segment-ids")));
        roulette.frameIds().addAll(uuids(section.getStringList("frame-ids")));
        roulette.needleIds().addAll(uuids(section.getStringList("needle-ids")));
        roulette.decorIds().addAll(uuids(section.getStringList("decor-ids")));
        roulette.setInteractionId(uuid(section.getString("interaction-id")));
        roulette.setHologramId(uuid(section.getString("hologram-id")));
        roulette.setCoreId(uuid(section.getString("core-id")));
        roulette.setRings(section.getInt("rings", 1));
        roulette.setStructureVersion(section.getInt("structure-version", 1));
        return roulette;
    }

    private static List<String> strings(List<UUID> ids) {
        List<String> values = new ArrayList<>();
        for (UUID id : ids) {
            values.add(id.toString());
        }
        return values;
    }

    private static List<UUID> uuids(List<String> raw) {
        List<UUID> ids = new ArrayList<>();
        for (String value : raw) {
            try {
                ids.add(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
                // Entrada dañada: se omite.
            }
        }
        return ids;
    }

    private static UUID uuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
