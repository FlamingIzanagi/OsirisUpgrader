package com.osiris.upgrader.roulette;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class Roulette {
    private final UUID id;
    private final UUID owner;
    private final String worldName;
    private final UUID worldId;
    private final double x;
    private double y;
    private final double z;
    private final double baseY;
    private float yaw;
    private final int segments;
    private final double radius;
    private final double frameGap;
    private final boolean smallStands;
    private final boolean markerVisuals;
    private final List<UUID> segmentIds = new ArrayList<>();
    private final List<UUID> frameIds = new ArrayList<>();
    private final List<UUID> needleIds = new ArrayList<>();
    private final List<UUID> decorIds = new ArrayList<>();
    private UUID interactionId;
    private UUID hologramId;
    private UUID coreId;
    private boolean spinning;
    private boolean prizeShown;
    private int rings = 1;
    private int structureVersion = 1;
    private int displayEpoch;
    private final List<UUID> rewardIds = new ArrayList<>();

    public static final int CURRENT_STRUCTURE = 35;

    public Roulette(
            UUID id,
            UUID owner,
            String worldName,
            UUID worldId,
            double x,
            double y,
            double z,
            double baseY,
            float yaw,
            int segments,
            double radius,
            double frameGap,
            boolean smallStands,
            boolean markerVisuals
    ) {
        this.id = id;
        this.owner = owner;
        this.worldName = worldName;
        this.worldId = worldId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.baseY = baseY;
        this.yaw = yaw;
        this.segments = segments;
        this.radius = radius;
        this.frameGap = frameGap;
        this.smallStands = smallStands;
        this.markerVisuals = markerVisuals;
    }

    public UUID id() {
        return id;
    }

    public UUID owner() {
        return owner;
    }

    public String worldName() {
        return worldName;
    }

    public UUID worldId() {
        return worldId;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public void setCenterY(double y) {
        this.y = y;
    }

    public double z() {
        return z;
    }

    public double baseY() {
        return baseY;
    }

    public float yaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public int segments() {
        return segments;
    }

    public double radius() {
        return radius;
    }

    public double frameGap() {
        return frameGap;
    }

    public boolean smallStands() {
        return smallStands;
    }

    public boolean markerVisuals() {
        return markerVisuals;
    }

    public List<UUID> segmentIds() {
        return segmentIds;
    }

    public List<UUID> frameIds() {
        return frameIds;
    }

    public List<UUID> needleIds() {
        return needleIds;
    }

    public List<UUID> decorIds() {
        return decorIds;
    }

    public UUID interactionId() {
        return interactionId;
    }

    public void setInteractionId(UUID interactionId) {
        this.interactionId = interactionId;
    }

    public UUID hologramId() {
        return hologramId;
    }

    public void setHologramId(UUID hologramId) {
        this.hologramId = hologramId;
    }

    public UUID coreId() {
        return coreId;
    }

    public void setCoreId(UUID coreId) {
        this.coreId = coreId;
    }

    public boolean spinning() {
        return spinning;
    }

    public void setSpinning(boolean spinning) {
        this.spinning = spinning;
    }

    public boolean prizeShown() {
        return prizeShown;
    }

    public void setPrizeShown(boolean prizeShown) {
        this.prizeShown = prizeShown;
    }

    public int rings() {
        return Math.max(1, rings);
    }

    public void setRings(int rings) {
        this.rings = Math.max(1, rings);
    }

    public int structureVersion() {
        return structureVersion;
    }

    public void setStructureVersion(int structureVersion) {
        this.structureVersion = structureVersion;
    }

    public int displayEpoch() {
        return displayEpoch;
    }

    public int bumpDisplay() {
        return ++displayEpoch;
    }

    public List<UUID> rewardIds() {
        return rewardIds;
    }

    public World world() {
        World world = worldId == null ? null : Bukkit.getWorld(worldId);
        if (world != null) {
            return world;
        }
        return Bukkit.getWorld(worldName);
    }

    public Location center() {
        World world = world();
        return world == null ? null : new Location(world, x, y, z, facingYaw(), 0f);
    }

    public int chunkX() {
        return ((int) Math.floor(x)) >> 4;
    }

    public int chunkZ() {
        return ((int) Math.floor(z)) >> 4;
    }

    public float facingYaw() {
        return yaw + 180f;
    }

    public double rightX() {
        double radians = Math.toRadians(yaw);
        return -Math.cos(radians);
    }

    public double rightZ() {
        double radians = Math.toRadians(yaw);
        return -Math.sin(radians);
    }

    public Location point(double angle, double distance) {
        World world = world();
        if (world == null) {
            return null;
        }
        double horizontal = Math.sin(angle) * distance;
        double vertical = Math.cos(angle) * distance;
        return new Location(world, x + rightX() * horizontal, y + vertical, z + rightZ() * horizontal, facingYaw(), 0f);
    }

    public Location pointFacing(double angle, double distance, double towardPlayer) {
        Location point = point(angle, distance);
        if (point == null || towardPlayer == 0.0d) {
            return point;
        }
        double radians = Math.toRadians(yaw);
        double lookX = -Math.sin(radians);
        double lookZ = Math.cos(radians);
        point.add(-lookX * towardPlayer, 0.0d, -lookZ * towardPlayer);
        return point;
    }

    public List<UUID> allIds() {
        List<UUID> ids = new ArrayList<>();
        ids.addAll(segmentIds);
        ids.addAll(frameIds);
        ids.addAll(needleIds);
        ids.addAll(decorIds);
        ids.addAll(rewardIds);
        if (interactionId != null) {
            ids.add(interactionId);
        }
        if (hologramId != null) {
            ids.add(hologramId);
        }
        if (coreId != null) {
            ids.add(coreId);
        }
        return ids;
    }

    public void replaceIds(Collection<UUID> segments, Collection<UUID> frames, Collection<UUID> needles, Collection<UUID> decor) {
        segmentIds.clear();
        segmentIds.addAll(segments);
        frameIds.clear();
        frameIds.addAll(frames);
        needleIds.clear();
        needleIds.addAll(needles);
        decorIds.clear();
        decorIds.addAll(decor);
    }
}
