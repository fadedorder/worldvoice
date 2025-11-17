package org.faded.worldvoice.service;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.faded.worldvoice.config.WVConfig;
import org.faded.worldvoice.core.SkillCategory;
import org.faded.worldvoice.core.SlotLedger;
import org.faded.worldvoice.util.LogJsonL;
import org.faded.worldvoice.world.WorldVoiceGameRules;

import java.util.Iterator;
import java.util.UUID;


public class RefreshService {
    private long lastRunMs = 0L;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        long now = System.currentTimeMillis();
        long intervalMs = WVConfig.REFRESH_INTERVAL_MINUTES.get() * 60L * 1000L;
        if (lastRunMs != 0 && now - lastRunMs < intervalMs) return;
        lastRunMs = now;

        ServerLevel overworld = server.overworld();
        SlotLedger ledger = SlotLedger.get(overworld);
        int ttlDays = WVConfig.TTL_DAYS.get();
        long ttlMs = ttlDays * 24L * 60L * 60L * 1000L;

        for (SkillCategory cat : SkillCategory.values()) {
            if (!WorldVoiceGameRules.isEnabled(overworld, cat)) continue;
            var map = ledger.categories.get(cat);
            for (var entry : map.entrySet()) {
                var id = entry.getKey();
                var state = entry.getValue();
                Iterator<UUID> it = state.holders.iterator();
                while (it.hasNext()) {
                    UUID holder = it.next();
                    SlotLedger.PlayerRecord pr = ledger.players.get(holder);
                    if (pr == null) continue;
                    if (pr.refreshExempt) continue;
                    if (now - pr.lastSeenMs > ttlMs) {
                        it.remove();
                        state.epoch++;
                        LogJsonL.append(String.format("{\"ts\":\"%s\",\"event\":\"expire\",\"skillId\":\"%s\",\"category\":\"%s\",\"player\":\"%s\"}",
                                LogJsonL.iso8601(now), id, cat, holder));
                    }
                }
            }
        }
        ledger.setDirty();


        UltBlacklistService.get().cleanupExpired(now);
    }
}
