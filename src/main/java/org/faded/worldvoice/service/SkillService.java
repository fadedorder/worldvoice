package org.faded.worldvoice.service;

import com.github.manasmods.manascore.api.skills.ManasSkill;
import com.github.manasmods.manascore.api.skills.SkillAPI;
import com.github.manasmods.manascore.api.skills.capability.SkillStorage;
import com.github.manasmods.tensura.ability.TensuraSkillInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.faded.worldvoice.core.SkillCategory;
import org.faded.worldvoice.core.SkillConfigManager;
import org.faded.worldvoice.core.SlotLedger;
import org.faded.worldvoice.util.LogJsonL;
import org.faded.worldvoice.world.WorldVoiceGameRules;

import java.util.UUID;

public class SkillService {

    public boolean assign(ServerPlayer player, ResourceLocation skillId, boolean bypass, boolean noCost) {
        ServerLevel level = (ServerLevel) player.getLevel();
        SkillCategory cat = SkillConfigManager.INSTANCE.classify(skillId);
        if (cat == null || !WorldVoiceGameRules.isEnabled(level, cat)) {
            return false;
        }

        long now = System.currentTimeMillis();
        SlotLedger ledger = SlotLedger.get(level);
        UUID playerId = player.getUUID();
        SlotLedger.PlayerRecord pr = ledger.getOrCreatePlayer(playerId, player.getGameProfile().getName());
        pr.lastSeenMs = now;

        if (SkillConfigManager.INSTANCE.isLearningBlacklisted(cat, skillId) && !pr.hasBlacklistBypass(cat, skillId)) {
            return false;
        }

        SlotLedger.SkillState state = ledger.getOrCreateSkill(cat, skillId);
        state.capacity = SkillConfigManager.INSTANCE.getCapacity(cat, skillId);

        boolean alreadyHolder = state.holders.contains(playerId);
        boolean allowed = bypass || pr.hasBypass(cat, skillId) || alreadyHolder || state.holders.size() < state.capacity;
        if (!allowed) return false;

        ManasSkill skill = SkillAPI.getSkillRegistry().getValue(skillId);
        if (skill == null) return false;
        SkillStorage storage = SkillAPI.getSkillsFrom(player);
        boolean hasSkill = storage.getSkill(skill).isPresent();
        if (org.faded.worldvoice.config.WVConfig.REVOKE_SKILLS_ON_REFRESH.get()) {
            if (!hasSkill) {
                TensuraSkillInstance instance = new TensuraSkillInstance(skill);
                if (noCost) instance.getOrCreateTag().putBoolean("NoMagiculeCost", true);
                if (!storage.learnSkill(instance)) {
                    return false;
                }
            }
        }

        boolean added = false;
        if (!alreadyHolder) {
            added = state.holders.add(playerId);
            if (added) state.epoch++;
        }

        ledger.setDirty();

        if (added || !hasSkill) {
            LogJsonL.append(String.format("{\"ts\":\"%s\",\"event\":\"assign\",\"skillId\":\"%s\",\"category\":\"%s\",\"player\":\"%s\",\"name\":\"%s\"}",
                    LogJsonL.iso8601(now), skillId, cat, playerId, player.getGameProfile().getName()));
        }
        return true;
    }

    public boolean revoke(ServerPlayer player, ResourceLocation skillId, String reason) {
        ServerLevel level = (ServerLevel) player.getLevel();
        SkillCategory cat = SkillConfigManager.INSTANCE.classify(skillId);
        if (cat == null) return false;

        ManasSkill skill = SkillAPI.getSkillRegistry().getValue(skillId);
        if (skill == null) return false;
        if (org.faded.worldvoice.config.WVConfig.REVOKE_SKILLS_ON_REFRESH.get()) {
            SkillAPI.getSkillsFrom(player).forgetSkill(skill);
        }

        return removeHolder(level, player.getUUID(), skillId, cat, reason);
    }

    public void updateLastSeen(ServerLevel level, UUID playerId, String name, long now) {
        SlotLedger ledger = SlotLedger.get(level);
        SlotLedger.PlayerRecord pr = ledger.getOrCreatePlayer(playerId, name);
        pr.lastSeenMs = now;
        ledger.setDirty();
    }

    public boolean removeHolder(ServerLevel level, UUID playerId, ResourceLocation skillId, SkillCategory cat, String reason) {
        SlotLedger ledger = SlotLedger.get(level);
        SlotLedger.SkillState state = ledger.getOrCreateSkill(cat, skillId);
        boolean removed = state.holders.remove(playerId);
        if (removed) {
            state.waitlist.remove(playerId);
            state.epoch++;
            ledger.setDirty();
            LogJsonL.append(String.format("{\"ts\":\"%s\",\"event\":\"revoke\",\"skillId\":\"%s\",\"category\":\"%s\",\"player\":\"%s\",\"reason\":\"%s\"}",
                    LogJsonL.iso8601(System.currentTimeMillis()), skillId, cat, playerId, reason == null ? "" : reason));
        }
        return removed;
    }

    public boolean removeHolder(ServerLevel level, UUID playerId, ResourceLocation skillId, String reason) {
        SkillCategory cat = SkillConfigManager.INSTANCE.classify(skillId);
        if (cat == null) return false;
        return removeHolder(level, playerId, skillId, cat, reason);
    }

    public int clearAssignments(ServerLevel level, UUID playerId, String reason) {
        SlotLedger ledger = SlotLedger.get(level);
        int removed = 0;
        long now = System.currentTimeMillis();
        for (SkillCategory cat : SkillCategory.values()) {
            var map = ledger.categories.get(cat);
            for (var entry : map.entrySet()) {
                var state = entry.getValue();
                if (state.holders.remove(playerId)) {
                    removed++;
                    state.epoch++;
                    LogJsonL.append(String.format("{\"ts\":\"%s\",\"event\":\"revoke\",\"skillId\":\"%s\",\"category\":\"%s\",\"player\":\"%s\",\"reason\":\"%s\"}",
                            LogJsonL.iso8601(now), entry.getKey(), cat, playerId, reason == null ? "" : reason));
                }
                state.waitlist.remove(playerId);
            }
        }
        if (removed > 0) {
            ledger.setDirty();
        }
        return removed;
    }

    public int clearSkill(ServerLevel level, SkillCategory cat, ResourceLocation skillId, String reason) {
        SlotLedger ledger = SlotLedger.get(level);
        SlotLedger.SkillState state = ledger.getOrCreateSkill(cat, skillId);
        if (state.holders.isEmpty()) return 0;
        long now = System.currentTimeMillis();
        int removed = state.holders.size();
        for (UUID holder : state.holders) {
            LogJsonL.append(String.format("{\"ts\":\"%s\",\"event\":\"revoke\",\"skillId\":\"%s\",\"category\":\"%s\",\"player\":\"%s\",\"reason\":\"%s\"}",
                    LogJsonL.iso8601(now), skillId, cat, holder, reason == null ? "" : reason));
        }
        state.holders.clear();
        state.epoch++;
        ledger.setDirty();
        return removed;
    }

    public int clearSkill(ServerLevel level, ResourceLocation skillId, String reason) {
        SkillCategory cat = SkillConfigManager.INSTANCE.classify(skillId);
        if (cat == null) return 0;
        return clearSkill(level, cat, skillId, reason);
    }
}
