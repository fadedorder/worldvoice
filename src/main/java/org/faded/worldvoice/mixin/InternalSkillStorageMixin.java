package org.faded.worldvoice.mixin;

import com.github.manasmods.manascore.api.skills.ManasSkill;
import com.github.manasmods.manascore.api.skills.ManasSkillInstance;
import com.github.manasmods.manascore.capability.skill.InternalSkillStorage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.faded.worldvoice.service.SkillService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.Iterator;

@Mixin(value = InternalSkillStorage.class, remap = false)
public interface InternalSkillStorageMixin extends InternalSkillStorage {

    SkillService WORLDVOICE$SKILLS = new SkillService();

    /**
     * @author WorldVoice
     * @reason Ensure ledger stays in sync when Tensura drops a skill.
     */
    @Overwrite
    default void forgetSkill(ManasSkill skill) {
        if (skill == null) {
            return;
        }

        boolean removed = false;
        Iterator<ManasSkillInstance> iterator = this.getLearnedSkills().iterator();
        while (iterator.hasNext()) {
            ManasSkillInstance instance = iterator.next();
            ManasSkill learned = instance.getSkill();
            if (learned == skill) {
                iterator.remove();
                removed = true;
            }
        }

        if (removed) {
            Entity owner = this.getOwner();
            if (owner instanceof ServerPlayer serverPlayer) {
                ResourceLocation id = skill.getRegistryName();
                if (id != null) {
                    ServerLevel level = (ServerLevel) serverPlayer.getLevel();
                    WORLDVOICE$SKILLS.removeHolder(level, serverPlayer.getUUID(), id, "tensura-reset");
                }
            }
        }
    }
}
