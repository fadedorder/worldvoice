package org.faded.worldvoice.mixin;

import com.github.manasmods.manascore.api.skills.ManasSkill;
import com.github.manasmods.manascore.api.skills.ManasSkillInstance;
import com.github.manasmods.manascore.api.skills.SkillAPI;
import com.github.manasmods.manascore.capability.skill.EntitySkillCapabilityStorage;
import com.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.faded.worldvoice.config.WVConfig;
import org.faded.worldvoice.core.SkillCategory;
import org.faded.worldvoice.core.SkillConfigManager;
import org.faded.worldvoice.core.SlotLedger;
import org.faded.worldvoice.service.UltBlacklistService;
import org.faded.worldvoice.world.WorldVoiceGameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntitySkillCapabilityStorage.class, remap = false)
public abstract class EntitySkillCapabilityStorageMixin {

    @Inject(method = "learnSkill", at = @At("HEAD"), cancellable = true)
    private void worldvoice$learnSkillHead(ManasSkillInstance instance, CallbackInfoReturnable<Boolean> cir) {
        if (instance == null) return;
        Entity owner = ((EntitySkillCapabilityStorage)(Object)this).getOwner();
        if (!(owner instanceof ServerPlayer serverPlayer)) return;

        ManasSkill skill = instance.getSkill();
        if (skill == null) return;
        ResourceLocation id = skill.getRegistryName();
        if (id == null) return;

        SkillCategory cat;
        if (skill instanceof Skill tensuraSkill) {
            var type = tensuraSkill.getType();
            cat = type == Skill.SkillType.ULTIMATE ? SkillCategory.ULTIMATE : (type == Skill.SkillType.UNIQUE ? SkillCategory.UNIQUE : null);
        } else {
            cat = SkillConfigManager.INSTANCE.classify(id);
        }
        if (cat != SkillCategory.ULTIMATE && cat != SkillCategory.UNIQUE) return;

        ServerLevel level = (ServerLevel) serverPlayer.getLevel();
        if (!WorldVoiceGameRules.isEnabled(level, cat)) return;

        SlotLedger ledger = SlotLedger.get(level);
        SlotLedger.SkillState state = ledger.getOrCreateSkill(cat, id);
        state.capacity = SkillConfigManager.INSTANCE.getCapacity(cat, id);


        SlotLedger.PlayerRecord record0 = ledger.getOrCreatePlayer(serverPlayer.getUUID(), serverPlayer.getGameProfile().getName());
        if (SkillConfigManager.INSTANCE.isLearningBlacklisted(cat, id) && !record0.hasBlacklistBypass(cat, id)) {

            cir.setReturnValue(false);
            return;
        }


        if (state.holders.contains(serverPlayer.getUUID())) return;


        SlotLedger.PlayerRecord record = record0;
        boolean bypass = record != null && record.hasBypass(cat, id);

        if (!bypass && state.capacity >= 0 && state.holders.size() >= state.capacity) {
            long now = System.currentTimeMillis();
            if (WVConfig.ULT_BLACKLIST_ENABLED.get() && UltBlacklistService.get().isDenied(serverPlayer.getUUID(), id, state.epoch, now)) {
                cir.setReturnValue(false);
                return;
            }
            if (WVConfig.ULT_BLACKLIST_ENABLED.get()) {
                long ttlMs = WVConfig.ULT_BLACKLIST_TTL_SECONDS.get() * 1000L;
                UltBlacklistService.get().recordDenied(serverPlayer.getUUID(), id, state.epoch, now, ttlMs);
            }
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "learnSkill", at = @At("RETURN"))
    private void worldvoice$learnSkillTail(ManasSkillInstance instance, CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue())) return;
        if (instance == null) return;
        Entity owner = ((EntitySkillCapabilityStorage)(Object)this).getOwner();
        if (!(owner instanceof ServerPlayer serverPlayer)) return;

        ManasSkill skill = instance.getSkill();
        if (skill == null) return;
        ResourceLocation id = skill.getRegistryName();
        if (id == null) return;

        SkillCategory cat;
        if (skill instanceof Skill tensuraSkill) {
            var type = tensuraSkill.getType();
            cat = type == Skill.SkillType.ULTIMATE ? SkillCategory.ULTIMATE : (type == Skill.SkillType.UNIQUE ? SkillCategory.UNIQUE : null);
        } else {
            cat = SkillConfigManager.INSTANCE.classify(id);
        }
        if (cat != SkillCategory.ULTIMATE && cat != SkillCategory.UNIQUE) return;

        ServerLevel level = (ServerLevel) serverPlayer.getLevel();
        if (!WorldVoiceGameRules.isEnabled(level, cat)) return;

        SlotLedger ledger = SlotLedger.get(level);
        SlotLedger.SkillState state = ledger.getOrCreateSkill(cat, id);
        state.capacity = SkillConfigManager.INSTANCE.getCapacity(cat, id);

        boolean fromPlunder = org.faded.worldvoice.service.LearnSourceTracker.consumePlunder(serverPlayer.getUUID());
        boolean fromCreation = org.faded.worldvoice.service.LearnSourceTracker.consumeCreation(serverPlayer.getUUID());

        boolean shouldRegister = true;
        if (!org.faded.worldvoice.config.WVConfig.REGISTER_PLUNDER_AS_OWNED.get() && fromPlunder) {
            shouldRegister = false;
        }
        if (!org.faded.worldvoice.config.WVConfig.REGISTER_CREATION_AS_OWNED.get() && fromCreation) {
            shouldRegister = false;
        }

        if (shouldRegister) {
            var storage = SkillAPI.getSkillsFrom(serverPlayer);
            var maybeInstance = storage.getSkill(skill);
            if (maybeInstance.isPresent() && maybeInstance.get().isTemporarySkill()) {
                shouldRegister = false;
            }
        }

        if (shouldRegister && state.holders.add(serverPlayer.getUUID())) {
            state.epoch++;
            ledger.setDirty();
        }

        UltBlacklistService.get().clear(serverPlayer.getUUID(), id);
    }
}
