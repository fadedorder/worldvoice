package org.faded.worldvoice.mixin;

import com.github.manasmods.manascore.api.skills.ManasSkill;
import com.github.manasmods.manascore.api.skills.SkillAPI;
import com.github.manasmods.tensura.capability.race.TensuraPlayerCapability;
import com.github.manasmods.tensura.config.TensuraConfig;
import com.github.manasmods.tensura.world.TensuraGameRules;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import org.faded.worldvoice.core.SkillCategory;
import org.faded.worldvoice.core.SkillConfigManager;
import org.faded.worldvoice.core.SlotLedger;
import org.faded.worldvoice.service.SkillService;
import org.faded.worldvoice.world.WorldVoiceGameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Mixin(value = com.github.manasmods.tensura.menu.RaceSelectionMenu.class, remap = false)
public class RaceSelectionMenuMixin {
    private static final SkillService WV_SKILLS = new SkillService();

    @SuppressWarnings("unchecked")
    @Inject(method = "randomUniqueSkill", at = @At("HEAD"), cancellable = true)
    private static void worldvoice$randomUniqueSkill(Player player, boolean coverEP, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ServerLevel level = (ServerLevel) serverPlayer.getLevel();
        if (!WorldVoiceGameRules.isEnabled(level, SkillCategory.UNIQUE)) {
            return; // allow Tensura to handle when WV unique slots are disabled
        }

        int baseRolls = TensuraConfig.INSTANCE.skillsConfig.skillNumber.get();
        int bonusInterval = level.getGameRules().getInt(TensuraGameRules.RESET_COUNTER_BONUS_UNIQUE);
        int maxBonus = TensuraConfig.INSTANCE.skillsConfig.maxCounterBonus.get();
        int resetCount = TensuraPlayerCapability.getResetCounter(player);
        int bonus = bonusInterval > 0 ? Math.min(resetCount / bonusInterval, maxBonus) : 0;
        double additionalChance = TensuraConfig.INSTANCE.skillsConfig.additionalUniqueChance.get();

        List<String> primaryPool = copyPool(TensuraConfig.INSTANCE.skillsConfig.reincarnationSkills.get());
        List<String> secondaryPool = copyPool(TensuraConfig.INSTANCE.skillsConfig.secondSkills.get());

        RandomSource random = player.getRandom();
        int totalRolls = baseRolls + bonus;
        int granted = 0;
        SlotLedger ledger = SlotLedger.get(level);

        for (int roll = 1; roll <= totalRolls; roll++) {
            if (roll >= bonus + 2) {
                if (random.nextDouble() * 100.0d >= additionalChance) {
                    break;
                }
            }

            List<String> source = (baseRolls == 2 && roll == bonus + 2) ? secondaryPool : primaryPool;
            ResourceLocation next = pickNextSkill(serverPlayer, ledger, source, random);
            if (next == null) continue;

            boolean ok = WV_SKILLS.assign(serverPlayer, next, false, coverEP);
            if (ok) {
                granted++;
            }
        }

        // Always cancel to prevent the original logic from bypassing slot enforcement
        ci.cancel();
    }

    private static ResourceLocation pickNextSkill(ServerPlayer player, SlotLedger ledger, List<String> pool, RandomSource random) {
        if (pool == null || pool.isEmpty()) return null;
        List<ResourceLocation> candidates = new ArrayList<>(pool.size());
        for (String raw : pool) {
            if (raw == null || raw.isBlank()) continue;
            ResourceLocation id = ResourceLocation.tryParse(raw.trim().toLowerCase(Locale.ROOT));
            if (id != null) {
                candidates.add(id);
            }
        }
        if (candidates.isEmpty()) return null;

        while (!candidates.isEmpty()) {
            int idx = random.nextInt(candidates.size());
            ResourceLocation id = candidates.remove(idx);
            if (!isUniqueManaged(id)) continue;
            if (!hasCapacity(player, ledger, id)) continue;
            if (alreadyHasSkill(player, id)) continue;
            return id;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> copyPool(Object raw) {
        if (raw instanceof List<?> list) {
            List<String> result = new ArrayList<>(list.size());
            for (Object obj : list) {
                if (obj instanceof String str) {
                    result.add(str);
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    private static boolean isUniqueManaged(ResourceLocation id) {
        return SkillConfigManager.INSTANCE.classify(id) == SkillCategory.UNIQUE;
    }

    private static boolean alreadyHasSkill(ServerPlayer player, ResourceLocation id) {
        ManasSkill skill = SkillAPI.getSkillRegistry().getValue(id);
        if (skill == null) return true;
        return SkillAPI.getSkillsFrom(player).getSkill(skill).isPresent();
    }

    private static boolean hasCapacity(ServerPlayer player, SlotLedger ledger, ResourceLocation id) {
        UUID playerId = player.getUUID();
        SlotLedger.SkillState state = ledger.getOrCreateSkill(SkillCategory.UNIQUE, id);
        state.capacity = SkillConfigManager.INSTANCE.getCapacity(SkillCategory.UNIQUE, id);
        if (state.holders.contains(playerId)) return true;
        SlotLedger.PlayerRecord record = ledger.players.get(playerId);
        if (record != null && record.hasBypass(SkillCategory.UNIQUE, id)) return true;
        return state.holders.size() < state.capacity;
    }
}
