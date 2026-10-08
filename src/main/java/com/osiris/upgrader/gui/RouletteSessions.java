package com.osiris.upgrader.gui;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RouletteSessions {
    private static final UUID PORTABLE = new UUID(0L, 1L);
    private final ConcurrentHashMap<UUID, Set<UpgraderHolder>> open = new ConcurrentHashMap<>();

    public boolean claim(UpgraderHolder session) {
        UUID key = key(session.rouletteId());
        Set<UpgraderHolder> viewers = open.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet());
        if (session.rouletteId() == null) {
            for (UpgraderHolder current : viewers) {
                if (!current.playerId().equals(session.playerId())) {
                    return false;
                }
            }
        }
        viewers.removeIf(current -> current != session && current.playerId().equals(session.playerId()));
        viewers.add(session);
        return true;
    }

    public void release(UpgraderHolder session) {
        if (session == null) {
            return;
        }
        UUID key = key(session.rouletteId());
        Set<UpgraderHolder> viewers = open.get(key);
        if (viewers == null) {
            return;
        }
        viewers.remove(session);
        if (viewers.isEmpty()) {
            open.remove(key, viewers);
        }
    }

    public List<UpgraderHolder> viewing(UUID rouletteId) {
        Set<UpgraderHolder> viewers = open.get(key(rouletteId));
        if (viewers == null || viewers.isEmpty()) {
            return List.of();
        }
        return List.copyOf(viewers);
    }

    public UUID occupant(UUID rouletteId) {
        for (UpgraderHolder session : viewing(rouletteId)) {
            return session.playerId();
        }
        return null;
    }

    private static UUID key(UUID rouletteId) {
        return rouletteId == null ? PORTABLE : rouletteId;
    }
}
