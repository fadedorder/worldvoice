package org.faded.worldvoice.service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class LearnSourceTracker {
    private static final long TTL_MS = 2000L;
    private static final Map<UUID, Long> PLUNDER = new HashMap<>();
    private static final Map<UUID, Long> CREATION = new HashMap<>();

    private LearnSourceTracker() {}

    public static void recordPlunder(UUID id) {
        if (id == null) return;
        long now = System.currentTimeMillis();
        synchronized (PLUNDER) {
            PLUNDER.put(id, now + TTL_MS);
        }
    }

    public static boolean consumePlunder(UUID id) {
        if (id == null) return false;
        long now = System.currentTimeMillis();
        synchronized (PLUNDER) {
            cleanup(PLUNDER, now);
            Long until = PLUNDER.remove(id);
            return until != null && until > now;
        }
    }

    public static void recordCreation(UUID id) {
        if (id == null) return;
        long now = System.currentTimeMillis();
        synchronized (CREATION) {
            CREATION.put(id, now + TTL_MS);
        }
    }

    public static boolean consumeCreation(UUID id) {
        if (id == null) return false;
        long now = System.currentTimeMillis();
        synchronized (CREATION) {
            cleanup(CREATION, now);
            Long until = CREATION.remove(id);
            return until != null && until > now;
        }
    }

    private static void cleanup(Map<UUID, Long> map, long now) {
        Iterator<Map.Entry<UUID, Long>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() <= now) it.remove();
        }
    }
}
