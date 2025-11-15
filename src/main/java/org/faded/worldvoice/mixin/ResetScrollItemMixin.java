package org.faded.worldvoice.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.faded.worldvoice.service.SkillService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = com.github.manasmods.tensura.item.custom.ResetScrollItem.class, remap = false)
public class ResetScrollItemMixin {
    private static final SkillService WV_SKILLS = new SkillService();

    @Inject(method = "resetEverything", at = @At("TAIL"))
    private static void worldvoice$resetEverything(ServerPlayer player, CallbackInfo ci) {
        if (player == null) return;
        ServerLevel level = (ServerLevel) player.getLevel();
        WV_SKILLS.clearAssignments(level, player.getUUID(), "reset");
    }
}
