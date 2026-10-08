package com.osiris.upgrader.roulette;

import com.osiris.upgrader.OsirisUpgraderPlugin;
import com.osiris.upgrader.config.ConfigManager;
import com.osiris.upgrader.config.Keys;
import com.osiris.upgrader.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Gate;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class RouletteFactory {
    private static final int COLOR_BANDS = 5;
    private static final int BORDER_BANDS = 2;
    private static final double FULL_HEAD = 1.4375d;
    private static final double SMALL_HEAD = 0.71875d;
    private static final double NEEDLE_FORWARD = 0.36d;
    private static final float NEEDLE_THICKNESS = 0.065f;
    private static final double NEEDLE_LENGTH_FACTOR = 0.96d;
    private static final double INNER_RADIUS = 0.30d;
    private static final double BORDER_THICKNESS = 0.42d;
    private static final double WHEEL_SCALE = 1.65d;
    private static final float FLAT_DEPTH = 0.045f;
    private static final double DISC_FORWARD = 0.32d;
    private static final double SUPPORT_BACK = -0.20d;
    private static final double GATE_FORWARD = 0.50d;
    private static final double CENTER_FORWARD = 0.72d;
    private static final float CENTER_BUTTON = 0.82f;
    private static final float CENTER_BUTTON_DEPTH = 0.16f;
    private static final float CENTER_BUTTON_FORWARD = 0.40f;
    private static final float CENTER_BUTTON_LIFT = -0.18f;
    private static final double CENTER_DROP = 0.72d;
    private static final EquipmentSlot[] LOCKED_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
            EquipmentSlot.HAND,
            EquipmentSlot.OFF_HAND,
            EquipmentSlot.BODY
    };

    private final OsirisUpgraderPlugin plugin;

    public RouletteFactory(OsirisUpgraderPlugin plugin) {
        this.plugin = plugin;
    }

    public Roulette spawn(Location base, Player owner) {
        World world = base.getWorld();
        if (world == null) {
            return null;
        }
        ConfigManager.RouletteSettings settings = plugin.configs().roulette();
        double x = base.getBlockX() + 0.5d;
        double z = base.getBlockZ() + 0.5d;
        double baseY = base.getBlockY();
        double centerY = groundedCenter(baseY, settings.radius());
        Roulette roulette = new Roulette(
                UUID.randomUUID(),
                owner.getUniqueId(),
                world.getName(),
                world.getUID(),
                x,
                centerY,
                z,
                baseY,
                snapYaw(owner.getLocation().getYaw()),
                settings.segments(),
                settings.radius(),
                settings.frameGap(),
                false,
                settings.markerVisuals()
        );
        build(roulette, settings);
        return roulette;
    }

    public void rebuild(Roulette roulette) {
        removeEntities(roulette);
        roulette.setYaw(snapYaw(roulette.yaw()));
        roulette.setCenterY(groundedCenter(roulette.baseY(), roulette.radius()));
        build(roulette, plugin.configs().roulette());
        plugin.roulettes().refreshShowcase(roulette);
    }

    public int removeEntities(Roulette roulette) {
        int removed = 0;
        Set<UUID> gone = new HashSet<>();
        for (UUID id : roulette.allIds()) {
            Entity entity = org.bukkit.Bukkit.getEntity(id);
            if (entity != null && gone.add(entity.getUniqueId())) {
                entity.remove();
                removed++;
            }
        }
        roulette.rewardIds().clear();
        roulette.setHologramId(null);
        roulette.setInteractionId(null);
        roulette.setCoreId(null);
        World world = roulette.world();
        Location center = roulette.center();
        if (world == null || center == null || !world.isChunkLoaded(roulette.chunkX(), roulette.chunkZ())) {
            return removed;
        }
        double reach = Math.max(roulette.radius() + BORDER_THICKNESS + 3.0d, roulette.y() - roulette.baseY() + 2.0d);
        for (Entity entity : world.getNearbyEntities(center, reach, reach, reach)) {
            if (!gone.add(entity.getUniqueId())) {
                continue;
            }
            String id = entity.getPersistentDataContainer().get(Keys.ROULETTE_ID, PersistentDataType.STRING);
            if (roulette.id().toString().equals(id)) {
                entity.remove();
                removed++;
            }
        }
        return removed;
    }

    public void paintIdle(Roulette roulette) {
        clearReward(roulette);
        ConfigManager.RouletteSettings settings = plugin.configs().roulette();
        List<Material> palette = settings.idlePalette();
        paintSlices(roulette, (slice, slices) -> palette.get(slice % palette.size()));
        plugin.roulettes().refreshShowcase(roulette);
    }

    public void paintChance(Roulette roulette, double chance) {
        ConfigManager.RouletteSettings settings = plugin.configs().roulette();
        paintSlices(roulette, (slice, slices) -> {
            double angle = (Math.PI * 2.0d * slice) / slices;
            return WheelMath.inSuccessZone(angle, chance) ? settings.success() : settings.fail();
        });
    }

    public void showShowcase(Roulette roulette, ItemStack display, Component label) {
        if (roulette.spinning() || roulette.prizeShown() || display == null || !centerLoaded(roulette)) {
            return;
        }
        clearCenter(roulette);
        spawnCenterItem(roulette, display, label);
    }

    public void showReward(Roulette roulette, double chance, ItemStack display, Component label) {
        clearCenter(roulette);
        if (display == null || !centerLoaded(roulette)) {
            return;
        }
        roulette.setPrizeShown(true);
        spawnCenterItem(roulette, display, label);
    }

    public void clearReward(Roulette roulette) {
        clearCenter(roulette);
    }

    private void spawnCenterItem(Roulette roulette, ItemStack display, Component label) {
        Location spot = itemOnGold(roulette);
        Location nameSpot = centerSpot(roulette);
        if (spot == null || nameSpot == null) {
            return;
        }
        ItemStack icon = display.clone();
        icon.setAmount(1);
        float yaw = roulette.facingYaw();
        spot.setYaw(yaw);
        spot.setPitch(0f);
        boolean solid = solidModel(icon);
        ItemDisplay shown = spot.getWorld().spawn(spot, ItemDisplay.class, entity -> {
            entity.setItemStack(icon);
            entity.setItemDisplayTransform(solid
                    ? ItemDisplay.ItemDisplayTransform.FIXED
                    : ItemDisplay.ItemDisplayTransform.GUI);
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setRotation(yaw, 0f);
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.setShadowRadius(0f);
            entity.setShadowStrength(0f);
            float size = displaySize(icon, solid);
            entity.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, solid ? 0.12f : 0f),
                    new Quaternionf(),
                    new Vector3f(size, size, size),
                    new Quaternionf()
            ));
            entity.setPersistent(true);
            entity.setInterpolationDuration(0);
            entity.setTeleportDuration(0);
            tag(entity, roulette.id(), "reward-item");
        });
        hideFromBedrock(shown);
        roulette.rewardIds().add(shown.getUniqueId());
        if (label == null) {
            return;
        }
        Location name = nameSpot.clone();
        name.setYaw(yaw);
        name.setPitch(0f);
        name.setY(nameSpot.getY() - 0.29d);
        TextDisplay hologram = name.getWorld().spawn(name, TextDisplay.class, text -> {
            text.text(label.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            text.setBillboard(Display.Billboard.FIXED);
            text.setAlignment(TextDisplay.TextAlignment.CENTER);
            text.setRotation(yaw, 0f);
            text.setSeeThrough(false);
            text.setDefaultBackground(false);
            text.setBackgroundColor(org.bukkit.Color.fromARGB(0xE0000000));
            text.setShadowed(false);
            text.setLineWidth(220);
            text.setPersistent(true);
            text.setInterpolationDuration(0);
            text.setTeleportDuration(0);
            tag(text, roulette.id(), "reward-label");
        });
        hideFromBedrock(hologram);
        roulette.rewardIds().add(hologram.getUniqueId());
        syncBedrockReward(roulette, icon, label);
    }

    private Location centerSpot(Roulette roulette) {
        Location spot = roulette.pointFacing(0.0d, 0.0d, CENTER_FORWARD);
        if (spot != null) {
            spot.setY(roulette.y() - 0.54d);
        }
        return spot;
    }

    private float displaySize(ItemStack stack, boolean solid) {
        if (!solid) {
            return 0.55f;
        }
        Material type = stack.getType();
        if (type == Material.BOW || type == Material.CROSSBOW) {
            return 0.22f;
        }
        return 0.7f;
    }

    private boolean solidModel(ItemStack stack) {
        Material type = stack.getType();
        if (type == Material.SHIELD
                || type == Material.TRIDENT
                || type == Material.BOW
                || type == Material.CROSSBOW
                || type == Material.FISHING_ROD
                || type == Material.SPYGLASS
                || type == Material.GOAT_HORN
                || type == Material.BRUSH
                || type == Material.MACE) {
            return true;
        }
        if (plugin.nexo().idFromItem(stack) != null) {
            return true;
        }
        org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.hasItemModel();
    }

    private Location itemOnGold(Roulette roulette) {
        double face = CENTER_BUTTON_FORWARD + (CENTER_BUTTON_DEPTH / 2.0d) + 0.03d;
        Location spot = roulette.pointFacing(0.0d, 0.0d, face);
        if (spot != null) {
            spot.setY(roulette.y());
        }
        return spot;
    }

    private boolean centerLoaded(Roulette roulette) {
        World world = roulette.world();
        return world != null && world.isChunkLoaded(roulette.chunkX(), roulette.chunkZ());
    }

    private void clearCenter(Roulette roulette) {
        for (UUID id : roulette.rewardIds()) {
            Entity entity = org.bukkit.Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        roulette.rewardIds().clear();
        roulette.setPrizeShown(false);
        World world = roulette.world();
        Location center = roulette.center();
        if (world == null || center == null || !world.isChunkLoaded(roulette.chunkX(), roulette.chunkZ())) {
            return;
        }
        double reach = roulette.radius() + 2.0d;
        for (Entity entity : world.getNearbyEntities(center, reach, reach, reach)) {
            String id = entity.getPersistentDataContainer().get(Keys.ROULETTE_ID, PersistentDataType.STRING);
            String role = entity.getPersistentDataContainer().get(Keys.ROLE, PersistentDataType.STRING);
            if (roulette.id().toString().equals(id) && ("reward-item".equals(role) || "reward-label".equals(role))) {
                entity.remove();
            }
        }
    }

    public void moveNeedle(Roulette roulette, double angle) {
        for (UUID id : roulette.needleIds()) {
            Entity entity = org.bukkit.Bukkit.getEntity(id);
            if (entity instanceof BlockDisplay display) {
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(0);
                display.setTransformation(needleBar(roulette, angle));
            }
        }
    }

    public void parkNeedle(Roulette roulette) {
        moveNeedle(roulette, 0.0d);
    }

    public void refreshHologram(Roulette roulette) {
        Entity entity = roulette.hologramId() == null ? null : org.bukkit.Bukkit.getEntity(roulette.hologramId());
        if (entity != null) {
            entity.remove();
        }
        roulette.setHologramId(null);
    }

    private void build(Roulette roulette, ConfigManager.RouletteSettings settings) {
        List<UUID> segments = new ArrayList<>();
        List<UUID> frames = new ArrayList<>();
        List<UUID> needles = new ArrayList<>();
        List<UUID> decor = new ArrayList<>();
        int slices = Math.max(8, roulette.segments());
        World world = roulette.world();
        if (world != null) {
            Location origin = new Location(world, roulette.x(), roulette.y(), roulette.z());
            List<Material> palette = settings.idlePalette();
            double coloredOuter = roulette.radius() * WHEEL_SCALE;
            double borderOuter = coloredOuter + BORDER_THICKNESS * WHEEL_SCALE;
            spawnAnnulus(origin, roulette, segments, INNER_RADIUS, coloredOuter, COLOR_BANDS, slices, palette, true);
            spawnAnnulus(origin, roulette, frames, coloredOuter, borderOuter, BORDER_BANDS, slices, palette, false);
            BlockDisplay needle = spawnDisplay(origin, Material.WHITE_STAINED_GLASS, roulette, "needle");
            needle.setTransformation(needleBar(roulette, 0.0d));
            needles.add(needle.getUniqueId());
            spawnSupport(world, roulette, settings, decor);
            BlockDisplay button = spawnDisplay(origin, Material.GOLD_BLOCK, roulette, "center-base");
            button.setTransformation(centerButton(roulette));
            decor.add(button.getUniqueId());
            float facing = roulette.facingYaw();
            Location hub = roulette.point(0.0d, 0.0d);
            ArmorStand core = spawnStand(hub == null
                    ? new Location(world, roulette.x(), roulette.y(), roulette.z(), facing, 0f)
                    : hub, null, roulette, "core", false, false, false);
            roulette.setCoreId(core.getUniqueId());
            float diameter = (float) ((roulette.radius() * WHEEL_SCALE + BORDER_THICKNESS * WHEEL_SCALE) * 2.0d + 0.8d);
            Interaction interaction = world.spawn(
                    new Location(world, roulette.x(), roulette.y() - roulette.radius() - 0.2d, roulette.z()),
                    Interaction.class,
                    entity -> {
                        entity.setInteractionWidth(diameter);
                        entity.setInteractionHeight(diameter);
                        entity.setResponsive(true);
                        entity.setPersistent(true);
                        tag(entity, roulette.id(), "interaction");
                    }
            );
            roulette.setInteractionId(interaction.getUniqueId());
        }
        roulette.setRings(COLOR_BANDS);
        roulette.setHologramId(null);
        roulette.setStructureVersion(Roulette.CURRENT_STRUCTURE);
        roulette.replaceIds(segments, frames, needles, decor);
        moveNeedle(roulette, 0.0d);
        spawnBedrockLayer(roulette);
    }

    public void ensureBedrockLayer(Roulette roulette) {
        if (!plugin.bedrock().isHooked() || hasBedrockLayer(roulette)) {
            return;
        }
        spawnBedrockLayer(roulette);
        plugin.bedrockView().refreshOnline();
    }

    public void syncBedrockReward(Roulette roulette, ItemStack display, Component label) {
        ArmorStand stand = bedrockReward(roulette);
        if (stand == null) {
            return;
        }
        ItemStack icon = display == null || display.getType().isAir() ? null : display.clone();
        if (icon != null) {
            icon.setAmount(1);
        }
        setHelmet(stand, icon);
        stand.customName(label);
        stand.setCustomNameVisible(label != null);
    }

    private void spawnBedrockLayer(Roulette roulette) {
        if (!plugin.bedrock().isHooked() || roulette.world() == null) {
            return;
        }
        ConfigManager.RouletteSettings settings = plugin.configs().roulette();
        List<Material> palette = settings.idlePalette();
        if (palette.isEmpty()) {
            palette = List.of(Material.RED_CONCRETE, Material.BLACK_CONCRETE);
        }
        int slices = Math.max(8, roulette.segments());
        double ring = roulette.radius() * 1.15d;
        for (int slice = 0; slice < slices; slice++) {
            double angle = (Math.PI * 2.0d * slice) / slices;
            Location spot = roulette.pointFacing(angle, ring, DISC_FORWARD);
            if (spot == null) {
                continue;
            }
            hideFromJava(spawnStand(spot, palette.get(slice % palette.size()), roulette, "bedrock-slice", false, true, true));
        }
        Location needle = roulette.pointFacing(0.0d, roulette.radius() * WHEEL_SCALE, DISC_FORWARD);
        if (needle != null) {
            hideFromJava(spawnStand(needle, Material.WHITE_CONCRETE, roulette, "bedrock-needle", false, true, true));
        }
        Location center = roulette.pointFacing(0.0d, 0.0d, CENTER_BUTTON_FORWARD);
        if (center != null) {
            hideFromJava(spawnStand(center, Material.GOLD_BLOCK, roulette, "bedrock-center", false, true, true));
        }
        Location reward = roulette.pointFacing(0.0d, 0.0d, CENTER_BUTTON_FORWARD + CENTER_BUTTON_DEPTH);
        if (reward != null) {
            hideFromJava(spawnStand(reward, null, roulette, "bedrock-reward", false, true, true));
        }
    }

    private void hideFromBedrock(Entity entity) {
        if (entity == null || !plugin.bedrock().isHooked()) {
            return;
        }
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (plugin.bedrock().isBedrock(player)) {
                player.hideEntity(plugin, entity);
            }
        }
    }

    private void hideFromJava(Entity entity) {
        if (entity == null) {
            return;
        }
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (!plugin.bedrock().isBedrock(player)) {
                player.hideEntity(plugin, entity);
            }
        }
    }

    private boolean hasBedrockLayer(Roulette roulette) {
        return bedrockReward(roulette) != null;
    }

    private ArmorStand bedrockReward(Roulette roulette) {
        World world = roulette.world();
        Location center = roulette.center();
        if (world == null || center == null || !world.isChunkLoaded(roulette.chunkX(), roulette.chunkZ())) {
            return null;
        }
        double reach = roulette.radius() * WHEEL_SCALE + 3.0d;
        for (Entity entity : world.getNearbyEntities(center, reach, reach, reach)) {
            if (!(entity instanceof ArmorStand stand)) {
                continue;
            }
            String id = entity.getPersistentDataContainer().get(Keys.ROULETTE_ID, PersistentDataType.STRING);
            String role = entity.getPersistentDataContainer().get(Keys.ROLE, PersistentDataType.STRING);
            if (roulette.id().toString().equals(id) && "bedrock-reward".equals(role)) {
                return stand;
            }
        }
        return null;
    }

    private void spawnSupport(World world, Roulette roulette, ConfigManager.RouletteSettings settings, List<UUID> decor) {
        double baseY = roulette.baseY();
        Slab slab = (Slab) Material.SMOOTH_STONE_SLAB.createBlockData();
        slab.setType(Slab.Type.BOTTOM);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                decor.add(spawnDisplay(new Location(world, roulette.x() + dx - 0.5d, baseY, roulette.z() + dz - 0.5d),
                        slab.clone(), roulette, "platform").getUniqueId());
            }
        }
        double woodY = baseY + 0.5d;
        decor.add(spawnDisplay(new Location(world, roulette.x() - 0.5d, woodY, roulette.z() - 0.5d),
                settings.base(), roulette, "base").getUniqueId());
        double yaw = Math.toRadians(roulette.yaw());
        double towardX = Math.sin(yaw);
        double towardZ = -Math.cos(yaw);
        Gate gate = (Gate) Material.SPRUCE_FENCE_GATE.createBlockData();
        gate.setFacing(frontFace(towardX, towardZ));
        gate.setOpen(true);
        gate.setInWall(false);
        decor.add(spawnDisplay(new Location(world,
                        roulette.x() + towardX * GATE_FORWARD - 0.5d,
                        woodY,
                        roulette.z() + towardZ * GATE_FORWARD - 0.5d),
                gate, roulette, "gate").getUniqueId());
        double backX = -towardX * SUPPORT_BACK;
        double backZ = -towardZ * SUPPORT_BACK;
        double fenceStart = woodY + 1.0d;
        double visualCenter = roulette.y();
        int posts = Math.max(1, (int) Math.ceil(visualCenter - fenceStart));
        for (int post = 0; post < posts; post++) {
            decor.add(spawnDisplay(new Location(world, roulette.x() + backX - 0.5d, fenceStart + post, roulette.z() + backZ - 0.5d),
                    settings.post(), roulette, "post").getUniqueId());
        }
    }

    private static BlockFace frontFace(double towardX, double towardZ) {
        if (Math.abs(towardX) > Math.abs(towardZ)) {
            return towardX > 0.0d ? BlockFace.EAST : BlockFace.WEST;
        }
        return towardZ > 0.0d ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    private void spawnAnnulus(Location origin, Roulette roulette, List<UUID> ids, double inner, double outer,
            int bands, int slices, List<Material> palette, boolean colored) {
        double step = (Math.PI * 2.0d) / slices;
        double half = step / 2.0d;
        float cosHalf = (float) Math.cos(half);
        float sinHalf = (float) Math.sin(half);
        for (int slice = 0; slice < slices; slice++) {
            double angle = slice * step;
            for (int band = 0; band < bands; band++) {
                double r0 = inner + (outer - inner) * band / bands;
                double r1 = inner + (outer - inner) * (band + 1.0d) / bands;
                if (band > 0 || !colored) {
                    r0 -= 0.03d;
                }
                float width = (float) (2.0d * r1 * sinHalf * 1.04d);
                float height = (float) Math.max(0.05d, (r1 - r0) * cosHalf);
                double centerDist = (r0 + r1) * 0.5d * cosHalf;
                Material material = colored
                        ? palette.get(slice % palette.size())
                        : Material.POLISHED_DEEPSLATE;
                float toward = colored ? -0.006f - slice * 0.0008f : 0.004f;
                ids.add(spawnWedge(origin, roulette, angle, centerDist, width, height, toward, material,
                        colored ? "segment" : "frame", colored ? slice : -1).getUniqueId());
            }
        }
    }

    private BlockDisplay spawnWedge(Location origin, Roulette roulette, double angle, double centerDist, float width,
            float height, float towardOffset, Material material, String role, int slice) {
        BlockDisplay display = spawnDisplay(origin, material, roulette, role);
        if (slice >= 0) {
            display.getPersistentDataContainer().set(Keys.SLICE, org.bukkit.persistence.PersistentDataType.INTEGER, slice);
        }
        display.setTransformation(wedge(roulette, angle, centerDist, width, height, towardOffset));
        return display;
    }

    private static Transformation wedge(Roulette roulette, double angle, double centerDist, float width, float height,
            float towardOffset) {
        double yaw = Math.toRadians(roulette.yaw());
        float rightX = (float) -Math.cos(yaw);
        float rightZ = (float) -Math.sin(yaw);
        float towardX = (float) Math.sin(yaw);
        float towardZ = (float) -Math.cos(yaw);
        float[] basis = WheelMath.wedgeBasis(roulette.yaw(), angle);
        float tanX = basis[0];
        float tanY = basis[1];
        float tanZ = basis[2];
        float radX = basis[3];
        float radY = basis[4];
        float radZ = basis[5];
        Quaternionf rotation = new Quaternionf().setFromNormalized(new Matrix3f(
                tanX, radX, towardX,
                tanY, radY, 0f,
                tanZ, radZ, towardZ
        ));
        float forward = (float) DISC_FORWARD + towardOffset;
        Vector3f center = new Vector3f(
                rightX * (float) (Math.sin(angle) * centerDist) + towardX * forward,
                (float) (Math.cos(angle) * centerDist),
                rightZ * (float) (Math.sin(angle) * centerDist) + towardZ * forward
        );
        Vector3f localCenter = new Vector3f(width / 2f, height / 2f, FLAT_DEPTH / 2f);
        Vector3f translation = center.sub(rotation.transform(localCenter, new Vector3f()));
        return new Transformation(translation, rotation, new Vector3f(width, height, FLAT_DEPTH), new Quaternionf());
    }

    private void paintSlices(Roulette roulette, SliceColor color) {
        List<UUID> segments = roulette.segmentIds();
        int slices = Math.max(1, roulette.segments());
        if (segments.isEmpty()) {
            return;
        }
        for (UUID id : segments) {
            Entity entity = org.bukkit.Bukkit.getEntity(id);
            if (!(entity instanceof BlockDisplay display)) {
                continue;
            }
            Integer slice = display.getPersistentDataContainer().get(Keys.SLICE, org.bukkit.persistence.PersistentDataType.INTEGER);
            if (slice == null) {
                continue;
            }
            int index = Math.floorMod(slice, slices);
            display.setBlock(color.of(index, slices).createBlockData());
        }
    }

    private static int sliceCount(Roulette roulette) {
        int segments = roulette.segmentIds().size();
        if (segments == 0) {
            return Math.max(1, roulette.segments());
        }
        int rings = roulette.rings();
        if (rings > 1 && segments % rings == 0) {
            return segments / rings;
        }
        return segments;
    }

    static double ringRadius(double outer, int ring, int rings) {
        if (rings <= 1) {
            return outer;
        }
        double inner = Math.max(0.42d, outer * 0.36d);
        return inner + (outer - inner) * ring / (rings - 1.0d);
    }

    private static Transformation needleBar(Roulette roulette, double angle) {
        double yaw = Math.toRadians(roulette.yaw());
        float rx = (float) (-Math.cos(yaw) * Math.sin(angle));
        float ry = (float) Math.cos(angle);
        float rz = (float) (-Math.sin(yaw) * Math.sin(angle));
        float length = (float) (roulette.radius() * WHEEL_SCALE * NEEDLE_LENGTH_FACTOR);
        float thick = NEEDLE_THICKNESS;
        Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0f, 1f, 0f), new Vector3f(rx, ry, rz));
        Vector3f start = new Vector3f(thick / 2f, 0f, thick / 2f);
        Vector3f translation = new Vector3f((float) (Math.sin(yaw) * NEEDLE_FORWARD), 0f, (float) (-Math.cos(yaw) * NEEDLE_FORWARD));
        translation.sub(rotation.transform(start, new Vector3f()));
        return new Transformation(translation, rotation, new Vector3f(thick, length, thick), new Quaternionf());
    }

    private static Transformation centerButton(Roulette roulette) {
        double yaw = Math.toRadians(roulette.yaw());
        float rightX = (float) -Math.cos(yaw);
        float rightZ = (float) -Math.sin(yaw);
        float towardX = (float) Math.sin(yaw);
        float towardZ = (float) -Math.cos(yaw);
        Quaternionf rotation = new Quaternionf().setFromNormalized(new Matrix3f(
                rightX, 0f, towardX,
                0f, 1f, 0f,
                rightZ, 0f, towardZ
        ));
        Vector3f center = new Vector3f(towardX * CENTER_BUTTON_FORWARD, 0f, towardZ * CENTER_BUTTON_FORWARD);
        Vector3f localCenter = new Vector3f(CENTER_BUTTON / 2f, CENTER_BUTTON / 2f, CENTER_BUTTON_DEPTH / 2f);
        Vector3f translation = center.sub(rotation.transform(localCenter, new Vector3f()));
        return new Transformation(translation, rotation, new Vector3f(CENTER_BUTTON, CENTER_BUTTON, CENTER_BUTTON_DEPTH), new Quaternionf());
    }

    private ArmorStand spawnStand(Location visual, Material material, Roulette roulette, String role, boolean marker, boolean small, boolean alignHelmet) {
        Location location = alignHelmet ? helmetAnchor(visual, small) : visual;
        return location.getWorld().spawn(location, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setMarker(marker);
            stand.setSmall(small);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setCollidable(false);
            stand.setSilent(true);
            stand.setPersistent(true);
            stand.setRemoveWhenFarAway(false);
            stand.setCanTick(false);
            for (EquipmentSlot slot : LOCKED_SLOTS) {
                try {
                    stand.addEquipmentLock(slot, ArmorStand.LockType.ADDING_OR_CHANGING);
                    stand.addEquipmentLock(slot, ArmorStand.LockType.REMOVING_OR_CHANGING);
                } catch (IllegalArgumentException ignored) {
                    // Algún slot nuevo no se puede bloquear en un Armor Stand.
                }
            }
            if (material != null) {
                setHelmet(stand, new ItemStack(material));
            }
            tag(stand, roulette.id(), role);
        });
    }

    private void tag(Entity entity, UUID rouletteId, String role) {
        entity.getPersistentDataContainer().set(Keys.ROULETTE_ID, PersistentDataType.STRING, rouletteId.toString());
        entity.getPersistentDataContainer().set(Keys.ROLE, PersistentDataType.STRING, role);
        entity.addScoreboardTag("osiris_roulette");
    }

    private void setHelmet(UUID id, Material material) {
        Entity entity = org.bukkit.Bukkit.getEntity(id);
        if (entity instanceof ArmorStand stand) {
            setHelmet(stand, new ItemStack(material));
        } else if (entity instanceof BlockDisplay display) {
            display.setBlock(material.createBlockData());
        }
    }

    private static void setHelmet(ArmorStand stand, ItemStack helmet) {
        if (stand.getEquipment() != null) {
            stand.getEquipment().setHelmet(helmet);
        }
    }

    private BlockDisplay spawnDisplay(Location location, Material material, Roulette roulette, String role) {
        return spawnDisplay(location, material.createBlockData(), roulette, role);
    }

    private BlockDisplay spawnDisplay(Location location, BlockData block, Roulette roulette, String role) {
        BlockDisplay spawned = location.getWorld().spawn(location, BlockDisplay.class, display -> {
            display.setBlock(block);
            display.setBillboard(Display.Billboard.FIXED);
            display.setRotation(0f, 0f);
            display.setShadowRadius(0f);
            display.setShadowStrength(0f);
            display.setTeleportDuration(0);
            display.setInterpolationDuration(0);
            display.setPersistent(true);
            tag(display, roulette.id(), role);
        });
        hideFromBedrock(spawned);
        return spawned;
    }

    private static Location helmetAnchor(Location visual, boolean small) {
        Location copy = visual.clone();
        copy.subtract(0.0d, small ? SMALL_HEAD : FULL_HEAD, 0.0d);
        return copy;
    }

    static double groundedCenter(double baseY, double radius) {
        return baseY + 2.65d + radius + BORDER_THICKNESS;
    }

    static float snapYaw(float yaw) {
        float snapped = Math.round(yaw / 90.0f) * 90.0f;
        snapped %= 360.0f;
        if (snapped < 0.0f) {
            snapped += 360.0f;
        }
        return snapped;
    }

    @FunctionalInterface
    private interface SliceColor {
        Material of(int slice, int slices);
    }
}
