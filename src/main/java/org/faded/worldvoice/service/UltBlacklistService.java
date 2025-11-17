package org.faded.worldvoice.service;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Lightweight in-memory blacklist for ultimate/unique learns while slots are full.
 * Entries are scoped by (player, skill) and invalidated automatically when the
 * skill's epoch changes (capacity/holders change) or when TTL expires.
 */
public final class UltBlacklistService {

    private static final class Entry {
        final long untilMs;
        final int epoch;
        Entry(long untilMs, int epoch) {
            this.untilMs = untilMs;
            this.epoch = epoch;
        }
    }

    private static final UltBlacklistService INSTANCE = new UltBlacklistService();

    public static UltBlacklistService get() {
        return INSTANCE;
    }

    private final Map<UUID, Map<ResourceLocation, Entry>> map = new HashMap<>();

    private UltBlacklistService() {}

    public boolean isDenied(UUID player, ResourceLocation id, int currentEpoch, long nowMs) {
        Map<ResourceLocation, Entry> skills = map.get(player);
        if (skills == null) return false;
        Entry e = skills.get(id);
        if (e == null) return false;

        if (nowMs >= e.untilMs) {
            skills.remove(id);
            if (skills.isEmpty()) map.remove(player);
            return false;
        }

        if (e.epoch != currentEpoch) {
            skills.remove(id);
            if (skills.isEmpty()) map.remove(player);
            return false;
        }
        return true;
    }

    public void recordDenied(UUID player, ResourceLocation id, int currentEpoch, long nowMs, long ttlMs) {
        Map<ResourceLocation, Entry> skills = map.computeIfAbsent(player, k -> new HashMap<>());
        skills.put(id, new Entry(nowMs + Math.max(1, ttlMs), currentEpoch));
    }

    public void clear(UUID player, ResourceLocation id) {
        Map<ResourceLocation, Entry> skills = map.get(player);
        if (skills == null) return;
        skills.remove(id);
        if (skills.isEmpty()) map.remove(player);
    }

    public void cleanupExpired(long nowMs) {
        Iterator<Map.Entry<UUID, Map<ResourceLocation, Entry>>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Map<ResourceLocation, Entry>> e = it.next();
            Map<ResourceLocation, Entry> inner = e.getValue();
            inner.entrySet().removeIf(en -> nowMs >= en.getValue().untilMs);
            if (inner.isEmpty()) it.remove();
        }
    }
}

